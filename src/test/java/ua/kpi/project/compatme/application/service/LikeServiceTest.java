package ua.kpi.project.compatme.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.kpi.project.compatme.application.dto.RecordLikeResult;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.out.LikeNotificationPort;
import ua.kpi.project.compatme.application.port.out.LikeRepositoryPort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Like;
import ua.kpi.project.compatme.domain.model.Orientation;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link LikeService}: mutual-match detection, notifications, and the "Who Liked Me"
 * lifecycle (a like is pending until answered by a like back, and becomes pending again if the
 * original liker likes again).
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private ProfileRepositoryPort profileRepository;

    @Mock
    private LikeNotificationPort notificationPort;

    private final InMemoryLikes likes = new InMemoryLikes();
    private LikeService service;
    private final ProfileId a = ProfileId.generate();
    private final ProfileId b = ProfileId.generate();

    @BeforeEach
    void setUp() {
        service = new LikeService(likes, profileRepository, notificationPort);
        lenient().when(profileRepository.findById(a)).thenReturn(Optional.of(profile(a, "A")));
        lenient().when(profileRepository.findById(b)).thenReturn(Optional.of(profile(b, "B")));
    }

    @Test
    void firstLike_isNotMutual_notifiesAndAppearsInWhoLikedMe() {
        RecordLikeResult result = service.recordLike(a, b);

        assertThat(result.mutualMatch()).isFalse();
        verify(notificationPort).notifyNewLike(any(), any());
        assertThat(names(service.getProfilesWhoLikedMe(b))).containsExactly("A");
    }

    @Test
    void repeatedPendingLike_isNoOp_andDoesNotNotifyAgain() {
        service.recordLike(a, b);
        service.recordLike(a, b);

        verify(notificationPort, times(1)).notifyNewLike(any(), any());
        assertThat(likes.all).hasSize(1);
    }

    @Test
    void likeBack_isMutual_notifiesBoth_andRemovesLikerFromWhoLikedMe() {
        service.recordLike(a, b);

        RecordLikeResult result = service.recordLike(b, a);

        assertThat(result.mutualMatch()).isTrue();
        verify(notificationPort).notifyMutualMatch(any(), any());
        assertThat(service.getProfilesWhoLikedMe(b)).isEmpty();
        assertThat(service.getProfilesWhoLikedMe(a)).isEmpty();
    }

    @Test
    void likeAgainAfterMatch_makesLikerReappearInWhoLikedMe_andNotifies() {
        service.recordLike(a, b);
        service.recordLike(b, a);

        service.recordLike(a, b);

        assertThat(names(service.getProfilesWhoLikedMe(b))).containsExactly("A");
        assertThat(service.getProfilesWhoLikedMe(a)).isEmpty();
        verify(notificationPort, times(2)).notifyNewLike(any(), any());
    }

    @Test
    void likingBackAgainAfterReLike_completesTheMatchAgain() {
        service.recordLike(a, b);
        service.recordLike(b, a);
        service.recordLike(a, b);

        RecordLikeResult result = service.recordLike(b, a);

        assertThat(result.mutualMatch()).isTrue();
        assertThat(service.getProfilesWhoLikedMe(b)).isEmpty();
        verify(notificationPort, times(2)).notifyMutualMatch(any(), any());
    }

    @Test
    void legacyMutualPair_isHealedWhenOneSideLikesBack() {
        likes.save(new Like(a, b, Instant.now().minusSeconds(20)));
        likes.save(new Like(b, a, Instant.now().minusSeconds(10)));

        service.recordLike(b, a);

        assertThat(service.getProfilesWhoLikedMe(b)).isEmpty();
        assertThat(service.getProfilesWhoLikedMe(a)).isEmpty();
    }

    @Test
    void rejectLike_removesLikerFromWhoLikedMe_withoutNotifying() {
        service.recordLike(a, b);

        service.rejectLike(b, a);

        assertThat(service.getProfilesWhoLikedMe(b)).isEmpty();
        verify(notificationPort, times(1)).notifyNewLike(any(), any());
        verify(notificationPort, never()).notifyMutualMatch(any(), any());
    }

    @Test
    void rejectedLiker_reappearsIfLikesAgain() {
        service.recordLike(a, b);
        service.rejectLike(b, a);

        service.recordLike(a, b);

        assertThat(names(service.getProfilesWhoLikedMe(b))).containsExactly("A");
        verify(notificationPort, times(2)).notifyNewLike(any(), any());
    }

    @Test
    void rejectLike_isNoOp_whenNoSuchLike() {
        service.rejectLike(b, a);

        assertThat(likes.all).isEmpty();
    }

    @Test
    void recordLike_doesNotPersistWhenLikedProfileDoesNotExist() {
        ProfileId missing = ProfileId.generate();
        when(profileRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordLike(a, missing)).isInstanceOf(ProfileNotFoundException.class);

        assertThat(likes.all).isEmpty();
        verify(notificationPort, never()).notifyNewLike(any(), any());
        verify(notificationPort, never()).notifyMutualMatch(any(), any());
    }

    private static List<String> names(List<Profile> profiles) {
        return profiles.stream().map(Profile::displayName).toList();
    }

    private Profile profile(ProfileId id, String name) {
        return Profile.builder().id(id).displayName(name).age(30).gender(Gender.FEMALE)
                .orientation(Orientation.STRAIGHT).country("Ukraine").city("Kyiv")
                .selfDescription("About me").preferenceDescription("Looking for")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    /** Minimal upserting fake mirroring the Mongo adapter's contract. */
    private static final class InMemoryLikes implements LikeRepositoryPort {
        final List<Like> all = new ArrayList<>();

        @Override
        public Like save(Like like) {
            all.removeIf(l -> l.likerProfileId().equals(like.likerProfileId())
                    && l.likedProfileId().equals(like.likedProfileId()));
            all.add(like);
            return like;
        }

        @Override
        public Optional<Like> findByLikerAndLiked(ProfileId liker, ProfileId liked) {
            return all.stream().filter(l -> l.likerProfileId().equals(liker) && l.likedProfileId().equals(liked))
                    .findFirst();
        }

        @Override
        public void deleteByLikerAndLiked(ProfileId liker, ProfileId liked) {
            all.removeIf(l -> l.likerProfileId().equals(liker) && l.likedProfileId().equals(liked));
        }

        @Override
        public boolean existsByLikerAndLiked(ProfileId liker, ProfileId liked) {
            return findByLikerAndLiked(liker, liked).isPresent();
        }

        @Override
        public List<Like> findByLikedProfileId(ProfileId likedProfileId) {
            return all.stream().filter(l -> l.likedProfileId().equals(likedProfileId)).toList();
        }

        @Override
        public void deleteAllInvolvingProfile(ProfileId profileId) {
            all.removeIf(l -> l.likerProfileId().equals(profileId) || l.likedProfileId().equals(profileId));
        }
    }
}
