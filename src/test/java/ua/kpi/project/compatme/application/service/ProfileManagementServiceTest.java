package ua.kpi.project.compatme.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.kpi.project.compatme.application.dto.CreateOrUpdateProfileCommand;
import ua.kpi.project.compatme.application.port.out.LikeRepositoryPort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileEmbeddings;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;
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
    private ProfileRepositoryPort profileRepository;

    @Mock
    private LikeRepositoryPort likeRepository;

    @Test
    void createOrUpdateProfile_reusesExistingProfile_whenTelegramUserIdAlreadyRegistered_andNoProfileIdGiven() {
        ProfileManagementService service = new ProfileManagementService(profileRepository, likeRepository);
        Profile existing = profileWith(ProfileId.generate(), "existing-telegram-id");
        when(profileRepository.findByTelegramUserId("existing-telegram-id")).thenReturn(Optional.of(existing));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrUpdateProfileCommand command = new CreateOrUpdateProfileCommand(
                null, "existing-telegram-id", "New Display Name", 30, Gender.FEMALE, Set.of(),
                "updated self description text", "updated preference description text", null, null, null, null, null);

        Profile result = service.createOrUpdateProfile(command);

        // Same id as the existing profile -> an update, not a new document.
        assertThat(result.id()).isEqualTo(existing.id());
        assertThat(result.displayName()).isEqualTo("New Display Name");
        verify(profileRepository, never()).findById(any());
        verify(profileRepository).save(any());
    }

    @Test
    void createOrUpdateProfile_createsNewProfile_whenTelegramUserIdNotYetRegistered() {
        ProfileManagementService service = new ProfileManagementService(profileRepository, likeRepository);
        when(profileRepository.findByTelegramUserId("brand-new-telegram-id")).thenReturn(Optional.empty());
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrUpdateProfileCommand command = new CreateOrUpdateProfileCommand(
                null, "brand-new-telegram-id", "Fresh User", 25, Gender.MALE, Set.of(),
                "self description text here", "preference description text here", null, null, null, null, null);

        Profile result = service.createOrUpdateProfile(command);

        assertThat(result.telegramUserId()).isEqualTo("brand-new-telegram-id");
        assertThat(result.displayName()).isEqualTo("Fresh User");
    }

    @Test
    void createOrUpdateProfile_prefersExplicitProfileId_overTelegramUserIdLookup() {
        ProfileManagementService service = new ProfileManagementService(profileRepository, likeRepository);
        ProfileId explicitId = ProfileId.generate();
        Profile existing = profileWith(explicitId, "some-telegram-id");
        when(profileRepository.findById(explicitId)).thenReturn(Optional.of(existing));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrUpdateProfileCommand command = new CreateOrUpdateProfileCommand(
                explicitId.value(), "some-telegram-id", "Renamed", 25, Gender.MALE, Set.of(),
                "self description text here", "preference description text here", null, null, null, null, null);

        Profile result = service.createOrUpdateProfile(command);

        assertThat(result.id()).isEqualTo(explicitId);
        verify(profileRepository, never()).findByTelegramUserId(eq("some-telegram-id"));
    }

    @Test
    void deleteProfile_cascadesToLikeRepository() {
        ProfileManagementService service = new ProfileManagementService(profileRepository, likeRepository);
        ProfileId id = ProfileId.generate();
        when(profileRepository.existsById(id)).thenReturn(true);

        service.deleteProfile(id);

        verify(likeRepository).deleteAllInvolvingProfile(id);
        verify(profileRepository).deleteById(id);
    }

    private static Profile profileWith(ProfileId id, String telegramUserId) {
        Instant now = Instant.now();
        return new Profile(
                id, telegramUserId, "Old Name", 28, Gender.FEMALE, Set.of(),
                "original self description text", "original preference description text",
                ProfileEmbeddings.empty(), now, now);
    }
}
