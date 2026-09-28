package ua.kpi.project.compatme.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.application.dto.CreateOrUpdateProfileCommand;
import ua.kpi.project.compatme.application.port.in.GenerateEmbeddingsUseCase;
import ua.kpi.project.compatme.application.port.in.ProfileManagementUseCase;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Profile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Seeds MongoDB with a batch of sample profiles (from {@code classpath:sample-profiles.json`})
 * and eagerly generates their embeddings once, at startup — so the recommendation endpoint has a
 * non-trivial candidate pool to score against immediately.
 *
 * <p>Gated behind {@code app.seed-data.enabled=true} so this never runs unintentionally against a
 * production-like environment.
 *
 * <p>Sample profiles deliberately have no real {@code telegramUserId} — the dataset generator
 * cannot practically create a real Telegram account per synthetic profile. Idempotency across
 * re-runs is instead achieved via each entry's required {@code sampleKey}: a stable, human-chosen
 * string (e.g. {@code "seed-001"}) deterministically hashed into a {@link ua.kpi.project.compatme.domain.model.ProfileId}
 * (see {@link #deterministicProfileId}), so re-running this loader against an already-seeded
 * database updates the same profiles instead of duplicating them — without needing a Telegram
 * lookup at all.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed-data", name = "enabled", havingValue = "true")
public class ProfileDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProfileDataLoader.class);

    /** Namespaced so these deterministic ids can never collide with a randomly-generated {@code ProfileId}. */
    private static final String SAMPLE_ID_NAMESPACE = "compatme-sample-profile:";

    private final ProfileManagementUseCase profileManagementUseCase;
    private final GenerateEmbeddingsUseCase generateEmbeddingsUseCase;
    private final ObjectMapper objectMapper;
    private final Resource sampleProfilesResource;

    public ProfileDataLoader(
            ProfileManagementUseCase profileManagementUseCase,
            GenerateEmbeddingsUseCase generateEmbeddingsUseCase,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
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
            String deterministicId = deterministicProfileId(sample.sampleKey());
            Profile profile = profileManagementUseCase.createOrUpdateProfile(sample.toCommand(deterministicId));
            generateEmbeddingsUseCase.generateEmbeddings(profile.id());
        }
        log.info("Sample profile seeding complete.");
    }

    /**
     * Deterministically derives a stable {@code ProfileId} string from a sample dataset's
     * {@code sampleKey}, so re-seeding is idempotent without any database lookup. Uses
     * {@link UUID#nameUUIDFromBytes} (MD5-based, type-3 UUID) — deterministic for a given input,
     * unlike {@link UUID#randomUUID()}.
     */
    static String deterministicProfileId(String sampleKey) {
        return UUID.nameUUIDFromBytes((SAMPLE_ID_NAMESPACE + sampleKey).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** Deserialization target for entries in {@code sample-profiles.json}. */
    private record SampleProfile(
            /**
             * Required, stable key used only to derive this profile's deterministic id (see
             * {@link #deterministicProfileId}) — never stored or exposed anywhere else.
             */
            String sampleKey,

            /**
             * Optional: sample profiles have no real Telegram account, so this is expected to be
             * {@code null} here. Left in the schema so the same JSON shape also documents what a
             * real bot-created profile would carry.
             */
            String telegramUserId,

            String displayName,
            Integer age,
            Gender gender,
            List<Gender> seekingGenders,
            String selfDescription,
            String preferenceDescription,

            /**
             * Optional, thesis-evaluation-only metadata: synthetic personality archetype tags.
             * Purely descriptive — never read by the compatibility scoring/recommendation logic.
             */
            List<Integer> archetypeIds,

            /** Optional free-text country, used only by the location-scope recommendation filter. */
            String country,

            /** Optional free-text city, used only by the location-scope recommendation filter. */
            String city,

            /**
             * Optional photo URL — only the URL is stored, never image bytes. Must start with
             * {@code http://} or {@code https://} when present (enforced by the domain
             * {@code Profile} constructor).
             */
            String photoUrl) {

        CreateOrUpdateProfileCommand toCommand(String deterministicProfileId) {
            Set<Gender> seeking = seekingGenders == null ? Set.of() : seekingGenders.stream().collect(Collectors.toSet());
            return new CreateOrUpdateProfileCommand(
                    deterministicProfileId, telegramUserId, displayName, age, gender, seeking,
                    selfDescription, preferenceDescription, archetypeIds, country, city, photoUrl, null);
        }
    }
}
