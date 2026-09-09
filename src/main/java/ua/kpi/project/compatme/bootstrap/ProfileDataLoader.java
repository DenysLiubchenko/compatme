package ua.kpi.project.compatme.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.application.dto.CreateOrUpdateProfileCommand;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.in.GenerateEmbeddingsUseCase;
import ua.kpi.project.compatme.application.port.in.ProfileManagementUseCase;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Profile;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Seeds MongoDB with a batch of sample profiles (from {@code classpath:sample-profiles.json`})
 * and eagerly generates their embeddings once, at startup — so the recommendation endpoint has a
 * non-trivial candidate pool to score against immediately.
 *
 * <p>Gated behind {@code app.seed-data.enabled=true} so this never runs unintentionally against a
 * production-like environment. Idempotent by {@code telegramUserId}: re-running against an
 * already-seeded database just updates the existing profiles (and the embedding cache correctly
 * skips recomputation for unchanged text).
 */
@Component
@ConditionalOnProperty(prefix = "app.seed-data", name = "enabled", havingValue = "true")
public class ProfileDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProfileDataLoader.class);

    private final ProfileManagementUseCase profileManagementUseCase;
    private final GenerateEmbeddingsUseCase generateEmbeddingsUseCase;
    private final ObjectMapper objectMapper;
    private final Resource sampleProfilesResource;

    public ProfileDataLoader(
            ProfileManagementUseCase profileManagementUseCase,
            GenerateEmbeddingsUseCase generateEmbeddingsUseCase,
            ObjectMapper objectMapper,
            org.springframework.core.io.ResourceLoader resourceLoader) {
        this.profileManagementUseCase = profileManagementUseCase;
        this.generateEmbeddingsUseCase = generateEmbeddingsUseCase;
        this.objectMapper = objectMapper;
        this.sampleProfilesResource = resourceLoader.getResource("classpath:sample-profiles.json");
    }

    @Override
    public void run(String... args) throws Exception {
        if (!sampleProfilesResource.exists()) {
            log.warn("sample-profiles.json not found on classpath; skipping data seeding");
            return;
        }

        List<SampleProfile> sampleProfiles;
        try (InputStream inputStream = sampleProfilesResource.getInputStream()) {
            sampleProfiles = objectMapper.readValue(inputStream, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, SampleProfile.class));
        }

        log.info("Seeding {} sample profiles...", sampleProfiles.size());
        for (SampleProfile sample : sampleProfiles) {
            String existingId = findExistingProfileId(sample.telegramUserId());
            Profile profile = profileManagementUseCase.createOrUpdateProfile(sample.toCommand(existingId));
            generateEmbeddingsUseCase.generateEmbeddings(profile.id());
        }
        log.info("Sample profile seeding complete.");
    }

    /** Looks up an existing profile by {@code telegramUserId} so re-seeding updates rather than duplicates. */
    private String findExistingProfileId(String telegramUserId) {
        try {
            return profileManagementUseCase.getByTelegramUserId(telegramUserId).id().value();
        } catch (ProfileNotFoundException e) {
            return null;
        }
    }

    /** Deserialization target for entries in {@code sample-profiles.json}. */
    private record SampleProfile(
            String telegramUserId,
            String displayName,
            Integer age,
            Gender gender,
            List<Gender> seekingGenders,
            String selfDescription,
            String preferenceDescription) {

        CreateOrUpdateProfileCommand toCommand(String existingProfileId) {
            Set<Gender> seeking = seekingGenders == null ? Set.of() : seekingGenders.stream().collect(Collectors.toSet());
            return new CreateOrUpdateProfileCommand(
                    existingProfileId, telegramUserId, displayName, age, gender, seeking, selfDescription, preferenceDescription);
        }
    }
}
