package ua.kpi.project.compatme.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.kpi.project.compatme.application.dto.CreateOrUpdateProfileCommand;
import ua.kpi.project.compatme.application.port.out.LikeRepositoryPort;
import ua.kpi.project.compatme.application.port.out.PhotoStoragePort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.application.port.out.ProfileAttributeExtractionPort;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Orientation;
import ua.kpi.project.compatme.domain.model.OptionalProfileFields;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileEmbeddings;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the duplicate-profile bug: {@code POST /api/v1/profiles} (no explicit
 * {@code profileId}) for a {@code telegramUserId} that already has a profile must update that
 * existing document, not insert a second one — which used to violate
 * {@code ProfileDocument.telegramUserId}'s unique index (once it actually got created) and, before
 * that, silently broke {@code findByTelegramUserId} by producing a non-unique result.
 *
 * <p>Also covers that account deletion cascades to {@link LikeRepositoryPort} — deleting a
 * profile must not leave orphaned like records referencing it.
 */
@ExtendWith(MockitoExtension.class)
class ProfileManagementServiceTest {

    @Mock
    private PhotoStoragePort photoStorage;

    @Mock
    private ProfileRepositoryPort profileRepository;

    @Mock
    private LikeRepositoryPort likeRepository;

    @Mock
    private ProfileAttributeExtractionPort attributeExtraction;

    @Test
    void createOrUpdateProfile_reusesExistingProfile_whenTelegramUserIdAlreadyRegistered_andNoProfileIdGiven() {
        ProfileManagementService service = service();
        Profile existing = profileWith(ProfileId.generate(), "existing-telegram-id");
        when(profileRepository.findByTelegramUserId("existing-telegram-id")).thenReturn(Optional.of(existing));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrUpdateProfileCommand command = command(null, "existing-telegram-id", "New Display Name", Gender.FEMALE);

        Profile result = service.createOrUpdateProfile(command);

        // Same id as the existing profile -> an update, not a new document.
        assertThat(result.id()).isEqualTo(existing.id());
        assertThat(result.displayName()).isEqualTo("New Display Name");
        verify(profileRepository, never()).findById(any());
        verify(profileRepository).save(any());
    }

    @Test
    void createOrUpdateProfile_createsNewProfile_whenTelegramUserIdNotYetRegistered() {
        ProfileManagementService service = service();
        when(profileRepository.findByTelegramUserId("brand-new-telegram-id")).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrUpdateProfileCommand command = command(null, "brand-new-telegram-id", "Fresh User", Gender.MALE);

        Profile result = service.createOrUpdateProfile(command);

        assertThat(result.telegramUserId()).isEqualTo("brand-new-telegram-id");
        assertThat(result.displayName()).isEqualTo("Fresh User");
    }

    @Test
    void createOrUpdateProfile_prefersExplicitProfileId_overTelegramUserIdLookup() {
        ProfileManagementService service = service();
        ProfileId explicitId = ProfileId.generate();
        Profile existing = profileWith(explicitId, "some-telegram-id");
        when(profileRepository.findById(explicitId)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrUpdateProfileCommand command = command(explicitId.value(), "some-telegram-id", "Renamed", Gender.MALE);

        Profile result = service.createOrUpdateProfile(command);

        assertThat(result.id()).isEqualTo(explicitId);
        verify(profileRepository, never()).findByTelegramUserId(eq("some-telegram-id"));
    }

    @Test
    void deleteProfile_cascadesToLikeRepository() {
        ProfileManagementService service = service();
        ProfileId id = ProfileId.generate();
        Profile existing = profileWith(id, "tg").withPhotoUrns(List.of("profile-photos/a.jpg"), Instant.now());
        when(profileRepository.findById(id)).thenReturn(Optional.of(existing));

        service.deleteProfile(id);

        verify(likeRepository).deleteAllInvolvingProfile(id);
        verify(profileRepository).deleteById(id);
        verify(photoStorage).delete("profile-photos/a.jpg");
    }

    @Test
    void createOrUpdateProfile_preservesExistingPhotoUrns() {
        ProfileManagementService service = service();
        Profile existing = profileWith(ProfileId.generate(), "tg-photos")
                .withPhotoUrns(List.of("profile-photos/a.jpg"), Instant.now());
        when(profileRepository.findByTelegramUserId("tg-photos")).thenReturn(Optional.of(existing));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Profile result = service.createOrUpdateProfile(command(null, "tg-photos", "Renamed", Gender.FEMALE));

        assertThat(result.photoUrns()).containsExactly("profile-photos/a.jpg");
    }

    private static Profile profileWith(ProfileId id, String telegramUserId) {
        Instant now = Instant.now();
        return Profile.builder().id(id).telegramUserId(telegramUserId).displayName("Old Name").age(28)
                .gender(Gender.FEMALE).orientation(Orientation.STRAIGHT).country("United States").city("New York")
                .seekingGenders(Set.of(Gender.MALE)).selfDescription("original self description text")
                .preferenceDescription("original preference description text").embeddings(ProfileEmbeddings.empty())
                .createdAt(now).updatedAt(now).build();
    }

    private ProfileManagementService service() {
        return new ProfileManagementService(profileRepository, likeRepository, attributeExtraction, photoStorage);
    }

    private static CreateOrUpdateProfileCommand command(String id, String telegramId, String name, Gender gender) {
        return new CreateOrUpdateProfileCommand(id, telegramId, name, 25, gender, Orientation.STRAIGHT,
                Set.of(Gender.FEMALE), "self description text", "preference description text", null,
                "United States", "New York", List.of(), OptionalProfileFields.empty());
    }
}
