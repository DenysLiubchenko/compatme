package ua.kpi.project.compatme.application.service;

import org.springframework.stereotype.Service;
import ua.kpi.project.compatme.application.dto.CreateOrUpdateProfileCommand;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.in.ProfileManagementUseCase;
import ua.kpi.project.compatme.application.port.out.LikeRepositoryPort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.application.port.out.ProfileAttributeExtractionPort;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileEmbeddings;
import ua.kpi.project.compatme.domain.model.ProfileId;
import ua.kpi.project.compatme.domain.model.OptionalProfileFields;

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
    private final ProfileAttributeExtractionPort attributeExtraction;

    public ProfileManagementService(ProfileRepositoryPort profileRepository, LikeRepositoryPort likeRepository,
                                    ProfileAttributeExtractionPort attributeExtraction) {
        this.profileRepository = profileRepository;
        this.likeRepository = likeRepository;
        this.attributeExtraction = attributeExtraction;
    }

    @Override
    public Profile createOrUpdateProfile(CreateOrUpdateProfileCommand command) {
        Instant now = Instant.now();
        Profile existing = findExisting(command);

        if (existing == null) {
            var optionalFields = command.optionalFields() == null
                    ? attributeExtraction.extract(command.selfDescription(), command.preferenceDescription())
                    : command.optionalFields();
            command = withOptionalFields(command, optionalFields);
            ProfileId id = command.profileId() != null ? ProfileId.of(command.profileId()) : ProfileId.generate();
            Profile created = toProfileBuilder(command, id, now, now, ProfileEmbeddings.empty()).build();
            return profileRepository.save(created);
        }

        if (command.optionalFields() == null) {
            command = withOptionalFields(command, new OptionalProfileFields(
                    existing.status(), existing.bodyType(), existing.diet(), existing.drinks(), existing.drugs(),
                    existing.education(), existing.ethnicity(), existing.height(), existing.income(), existing.job(),
                    existing.lastOnline(), existing.offspring(), existing.pets(), existing.religion(), existing.sign(),
                    existing.smokes(), existing.speaks()));
        }

        // Preserve embedding-staleness detection: only clears an embedding if its source text changed.
        Profile updated = existing
                .withSelfDescription(command.selfDescription(), now)
                .withPreferenceDescription(command.preferenceDescription(), now);
        Profile rebuilt = toProfileBuilder(command, updated.id(), updated.createdAt(), now, updated.embeddings())
                .selfDescription(updated.selfDescription())
                .preferenceDescription(updated.preferenceDescription())
                .build();
        return profileRepository.save(rebuilt);
    }

    private CreateOrUpdateProfileCommand withOptionalFields(
            CreateOrUpdateProfileCommand command, OptionalProfileFields optionalFields) {
        return new CreateOrUpdateProfileCommand(command.profileId(), command.telegramUserId(), command.displayName(),
                command.age(), command.gender(), command.orientation(), command.seekingGenders(), command.selfDescription(),
                command.preferenceDescription(), command.archetypeIds(), command.country(), command.city(), command.photoUrls(),
                optionalFields);
    }

    private Profile.Builder toProfileBuilder(
            CreateOrUpdateProfileCommand command, ProfileId id, Instant createdAt, Instant updatedAt,
            ProfileEmbeddings embeddings) {
        OptionalProfileFields fields = command.optionalFields() == null
                ? OptionalProfileFields.empty() : command.optionalFields();
        return Profile.builder()
                .id(id).telegramUserId(command.telegramUserId()).displayName(command.displayName())
                .age(command.age()).gender(command.gender()).orientation(command.orientation())
                .country(command.country()).city(command.city()).seekingGenders(command.seekingGenders())
                .selfDescription(command.selfDescription()).preferenceDescription(command.preferenceDescription())
                .embeddings(embeddings).createdAt(createdAt).updatedAt(updatedAt)
                .archetypeIds(command.archetypeIds()).photoUrls(command.photoUrls())
                .status(fields.status()).bodyType(fields.bodyType())
                .diet(fields.diet()).drinks(fields.drinks()).drugs(fields.drugs()).education(fields.education())
                .ethnicity(fields.ethnicity()).height(fields.height()).income(fields.income()).job(fields.job())
                .lastOnline(fields.lastOnline()).offspring(fields.offspring()).pets(fields.pets())
                .religion(fields.religion()).sign(fields.sign()).smokes(fields.smokes()).speaks(fields.speaks());
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
