package ua.kpi.project.compatme.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.kpi.project.compatme.application.dto.RecordLikeResult;
import ua.kpi.project.compatme.application.port.out.LikeRepositoryPort;
import ua.kpi.project.compatme.application.port.out.LikeNotificationPort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Orientation;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link LikeService}'s mutual-match detection: liking someone who hasn't liked you
 * back is NOT a mutual match; liking someone who already liked you IS. A like is always recorded
 * regardless of mutuality — mutuality only changes what's reported back to the caller.
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeRepositoryPort likeRepository;

    @Mock
    private ProfileRepositoryPort profileRepository;

    @Mock
    private LikeNotificationPort notificationPort;

    @Test
    void recordLike_isNotMutual_whenNoReverseLikeExists() {
        LikeService service = new LikeService(likeRepository, profileRepository, notificationPort);
        ProfileId liker = ProfileId.generate();
        ProfileId liked = ProfileId.generate();
        when(likeRepository.existsByLikerAndLiked(liker, liked)).thenReturn(false);
        when(likeRepository.existsByLikerAndLiked(liked, liker)).thenReturn(false);
        when(profileRepository.findById(liker)).thenReturn(Optional.of(profile(liker, "Liker")));
        when(profileRepository.findById(liked)).thenReturn(Optional.of(profile(liked, "Liked")));

        RecordLikeResult result = service.recordLike(liker, liked);

        assertThat(result.mutualMatch()).isFalse();
        verify(likeRepository).save(any());
        verify(notificationPort).notifyNewLike(any(), any());
    }

    @Test
    void recordLike_isMutual_whenReverseLikeAlreadyExists() {
        LikeService service = new LikeService(likeRepository, profileRepository, notificationPort);
        ProfileId liker = ProfileId.generate();
        ProfileId liked = ProfileId.generate();
        when(likeRepository.existsByLikerAndLiked(liker, liked)).thenReturn(false);
        when(likeRepository.existsByLikerAndLiked(liked, liker)).thenReturn(true);
        when(profileRepository.findById(liker)).thenReturn(Optional.of(profile(liker, "Liker")));
        when(profileRepository.findById(liked)).thenReturn(Optional.of(profile(liked, "Liked")));

        RecordLikeResult result = service.recordLike(liker, liked);

        assertThat(result.mutualMatch()).isTrue();
        verify(likeRepository).save(any());
        verify(notificationPort).notifyMutualMatch(any(), any());
        verify(notificationPort, never()).notifyNewLike(any(), any());
    }

    @Test
    void recordLike_doesNotSaveDuplicate_whenAlreadyLiked() {
        LikeService service = new LikeService(likeRepository, profileRepository, notificationPort);
        ProfileId liker = ProfileId.generate();
        ProfileId liked = ProfileId.generate();
        when(likeRepository.existsByLikerAndLiked(liker, liked)).thenReturn(true);
        when(likeRepository.existsByLikerAndLiked(liked, liker)).thenReturn(false);

        service.recordLike(liker, liked);

        verify(likeRepository, never()).save(any());
        verify(notificationPort, never()).notifyNewLike(any(), any());
        verify(notificationPort, never()).notifyMutualMatch(any(), any());
    }

    private Profile profile(ProfileId id, String name) {
        return Profile.builder().id(id).displayName(name).age(30).gender(Gender.FEMALE)
                .orientation(Orientation.STRAIGHT).country("Ukraine").city("Kyiv")
                .selfDescription("About me").preferenceDescription("Looking for")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
}
