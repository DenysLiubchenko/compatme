package ua.kpi.project.compatme.application.service;

import org.springframework.stereotype.Service;
import ua.kpi.project.compatme.application.dto.CreateOrUpdateProfileCommand;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.in.ProfileManagementUseCase;
import ua.kpi.project.compatme.application.port.out.LikeRepositoryPort;
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
    private final LikeRepositoryPort likeRepository;

    public ProfileManagementService(ProfileRepositoryPort profileRepository, LikeRepositoryPort likeRepository) {
        this.profileRepository = profileRepository;
        this.likeRepository = likeRepository;
    }

    @Override
    public Profile createOrUpdateProfile(CreateOrUpdateProfileCommand command) {
        Instant now = Instant.now();
        Profile existing = findExisting(command);

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
                    command.archetypeIds(),
                    command.country(),
                    command.city(),
                    command.photoUrl(),
                    command.photoFileIds());
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
                command.archetypeIds(),
                command.country(),
                command.city(),
                command.photoUrl(),
                command.photoFileIds());
        return profileRepository.save(rebuilt);
    }

    /**
     * Resolves the profile to update, if any. Looks up by {@code command.profileId()} first
     * (explicit id, e.g. from {@code PUT /api/v1/profiles/{id}} or the seed loader's deterministic
     * ids); if that's absent but {@code telegramUserId} is present, falls back to looking up by
     * {@code telegramUserId} instead. This second lookup is what makes repeated
     * {@code POST /api/v1/profiles} calls for the same Telegram user (e.g. the bot's onboarding
     * flow re-run via {@code /start}) idempotent updates rather than duplicate documents — which
     * would otherwise violate {@code telegramUserId}'s unique index and, before that index existed,
     * silently broke {@code findByTelegramUserId} by creating a non-unique result.
     */
    private Profile findExisting(CreateOrUpdateProfileCommand command) {
        if (command.profileId() != null) {
            return profileRepository.findById(ProfileId.of(command.profileId())).orElse(null);
        }
        if (command.telegramUserId() != null && !command.telegramUserId().isBlank()) {
            return profileRepository.findByTelegramUserId(command.telegramUserId()).orElse(null);
        }
        return null;
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
        // Cascades likes on both sides (liker and liked) — orphaned like records referencing a
        // deleted profile would otherwise linger forever and pollute "Who Liked Me" for others.
        likeRepository.deleteAllInvolvingProfile(id);
        profileRepository.deleteById(id);
    }
}
