package ua.kpi.project.compatme.application.service;

import org.springframework.stereotype.Service;
import ua.kpi.project.compatme.application.dto.RecordLikeResult;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.in.GetProfilesWhoLikedMeUseCase;
import ua.kpi.project.compatme.application.port.in.RecordLikeUseCase;
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
public class LikeService implements RecordLikeUseCase, GetProfilesWhoLikedMeUseCase {

    private final LikeRepositoryPort likeRepository;
    private final ProfileRepositoryPort profileRepository;
    private final LikeNotificationPort notificationPort;

    public LikeService(LikeRepositoryPort likeRepository, ProfileRepositoryPort profileRepository,
                       LikeNotificationPort notificationPort) {
        this.likeRepository = likeRepository;
        this.profileRepository = profileRepository;
        this.notificationPort = notificationPort;
    }

    @Override
    public RecordLikeResult recordLike(ProfileId likerId, ProfileId likedId) {
        Profile liker = profileRepository.findById(likerId)
                .orElseThrow(() -> new ProfileNotFoundException(likerId.value()));
        Profile liked = profileRepository.findById(likedId)
                .orElseThrow(() -> new ProfileNotFoundException(likedId.value()));
        boolean newLike = !likeRepository.existsByLikerAndLiked(likerId, likedId);
        if (newLike) {
            likeRepository.save(new Like(likerId, likedId, Instant.now()));
        }
        boolean mutualMatch = likeRepository.existsByLikerAndLiked(likedId, likerId);
        if (newLike) {
            if (mutualMatch) {
                notificationPort.notifyMutualMatch(liker, liked);
            } else {
                notificationPort.notifyNewLike(liked, liker);
            }
        }
        return new RecordLikeResult(mutualMatch);
    }

    @Override
    public List<Profile> getProfilesWhoLikedMe(ProfileId profileId) {
        return likeRepository.findByLikedProfileId(profileId).stream()
                .map(like -> profileRepository.findById(like.likerProfileId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
