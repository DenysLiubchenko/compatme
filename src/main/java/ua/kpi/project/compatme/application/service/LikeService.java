package ua.kpi.project.compatme.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ua.kpi.project.compatme.application.dto.RecordLikeResult;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.in.GetProfilesWhoLikedMeUseCase;
import ua.kpi.project.compatme.application.port.in.RecordLikeUseCase;
import ua.kpi.project.compatme.application.port.in.RejectLikeUseCase;
import ua.kpi.project.compatme.application.port.out.LikeRepositoryPort;
import ua.kpi.project.compatme.application.port.out.LikeNotificationPort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.Like;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Implements the "like" concept — an explicit user action recorded independently of
 * {@code CompatibilityScorer}/aggregation strategies (see {@link Like}'s Javadoc). Orchestrates
 * {@link LikeRepositoryPort} and {@link ProfileRepositoryPort}; contains no MongoDB-specific
 * logic.
 */
@Service
public class LikeService implements RecordLikeUseCase, GetProfilesWhoLikedMeUseCase, RejectLikeUseCase {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);

    private final LikeRepositoryPort likeRepository;
    private final ProfileRepositoryPort profileRepository;
    private final LikeNotificationPort notificationPort;

    public LikeService(LikeRepositoryPort likeRepository, ProfileRepositoryPort profileRepository,
                       LikeNotificationPort notificationPort) {
        this.likeRepository = likeRepository;
        this.profileRepository = profileRepository;
        this.notificationPort = notificationPort;
    }

    /**
     * Records a like. A like stays visible in the liked profile's "Who Liked Me" until it is
     * answered; liking back makes the pair a mutual match and answers the pending like, removing it
     * from the list. If the original liker later likes again, the like becomes pending (and
     * notifies) again. Repeating a like that is still pending is a no-op.
     */
    @Override
    public RecordLikeResult recordLike(ProfileId likerId, ProfileId likedId) {
        Profile liker = profileRepository.findById(likerId)
                .orElseThrow(() -> new ProfileNotFoundException(likerId.value()));
        Profile liked = profileRepository.findById(likedId)
                .orElseThrow(() -> new ProfileNotFoundException(likedId.value()));
        Optional<Like> existing = likeRepository.findByLikerAndLiked(likerId, likedId);
        Optional<Like> reverse = likeRepository.findByLikerAndLiked(likedId, likerId);
        Instant now = Instant.now();

        if (reverse.isPresent() && !reverse.get().answered()) {
            // The liked profile was waiting for an answer: this like completes a mutual match.
            likeRepository.save(reverse.get().markedAnswered());
            likeRepository.save(existing.orElseGet(() -> new Like(likerId, likedId, now)).markedAnswered());
            log.info("Like {} -> {} recorded: mutual match", likerId.value(), likedId.value());
            notificationPort.notifyMutualMatch(liker, liked);
            return new RecordLikeResult(true);
        }

        boolean mutualMatch = reverse.isPresent();
        if (existing.isEmpty()) {
            likeRepository.save(new Like(likerId, likedId, now));
            log.info("Like {} -> {} recorded: new like", likerId.value(), likedId.value());
            notificationPort.notifyNewLike(liked, liker);
        } else if (existing.get().answered()) {
            likeRepository.save(existing.get().renewed(now));
            log.info("Like {} -> {} recorded: liked again after a match", likerId.value(), likedId.value());
            notificationPort.notifyNewLike(liked, liker);
        } else {
            log.info("Like {} -> {} ignored: already pending", likerId.value(), likedId.value());
        }
        return new RecordLikeResult(mutualMatch);
    }

    /** Declines a received like: it is removed (no notification), and reappears only if they like again. */
    @Override
    public void rejectLike(ProfileId profileId, ProfileId likerId) {
        likeRepository.deleteByLikerAndLiked(likerId, profileId);
        log.info("Like {} -> {} rejected", likerId.value(), profileId.value());
    }

    /** People whose like of this profile is still pending (not yet answered by a like back). */
    @Override
    public List<Profile> getProfilesWhoLikedMe(ProfileId profileId) {
        return likeRepository.findByLikedProfileId(profileId).stream()
                .filter(like -> !like.answered())
                .map(like -> profileRepository.findById(like.likerProfileId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
