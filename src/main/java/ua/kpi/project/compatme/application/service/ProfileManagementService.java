package ua.kpi.project.compatme.application.service;

import org.springframework.stereotype.Service;
import ua.kpi.project.compatme.application.dto.CreateOrUpdateProfileCommand;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.in.ProfileManagementUseCase;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileEmbeddings;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;
import java.util.List;

/**
 * Application service implementing basic profile CRUD. Orchestrates the domain model and the
 * {@link ProfileRepositoryPort} outbound port; contains no Spring Data or MongoDB-specific logic.
 */
@Service
public class ProfileManagementService implements ProfileManagementUseCase {

    private final ProfileRepositoryPort profileRepository;

    public ProfileManagementService(ProfileRepositoryPort profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public Profile createOrUpdateProfile(CreateOrUpdateProfileCommand command) {
        Instant now = Instant.now();
        Profile existing = command.profileId() == null
                ? null
                : profileRepository.findById(ProfileId.of(command.profileId())).orElse(null);

        if (existing == null) {
            ProfileId id = command.profileId() != null ? ProfileId.of(command.profileId()) : ProfileId.generate();
            Profile created = new Profile(
                    id,
                    command.telegramUserId(),
                    command.displayName(),
                    command.age(),
                    command.gender(),
                    command.seekingGenders(),
                    command.selfDescription(),
                    command.preferenceDescription(),
                    ProfileEmbeddings.empty(),
                    now,
                    now,
                    command.archetypeIds());
            return profileRepository.save(created);
        }

        // Preserve embedding-staleness detection: only clears an embedding if its source text changed.
        Profile updated = existing
                .withSelfDescription(command.selfDescription(), now)
                .withPreferenceDescription(command.preferenceDescription(), now);
        Profile rebuilt = new Profile(
                updated.id(),
                command.telegramUserId(),
                command.displayName(),
                command.age(),
                command.gender(),
                command.seekingGenders(),
                updated.selfDescription(),
                updated.preferenceDescription(),
                updated.embeddings(),
                updated.createdAt(),
                now,
                command.archetypeIds());
        return profileRepository.save(rebuilt);
    }

    @Override
    public Profile getProfile(ProfileId id) {
        return profileRepository.findById(id).orElseThrow(() -> new ProfileNotFoundException(id.value()));
    }

    @Override
    public Profile getByTelegramUserId(String telegramUserId) {
        return profileRepository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> new ProfileNotFoundException("telegramUserId=" + telegramUserId));
    }

    @Override
    public List<Profile> listProfiles() {
        return profileRepository.findAll();
    }

    @Override
    public void deleteProfile(ProfileId id) {
        if (!profileRepository.existsById(id)) {
            throw new ProfileNotFoundException(id.value());
        }
        profileRepository.deleteById(id);
    }
}
