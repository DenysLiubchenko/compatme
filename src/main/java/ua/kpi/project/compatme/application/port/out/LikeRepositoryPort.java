package ua.kpi.project.compatme.application.port.out;

import ua.kpi.project.compatme.domain.model.Like;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.util.List;

/**
 * Outbound port for persisting/querying {@link Like} records. Implemented by
 * {@code adapter.out.persistence} (MongoDB today), mirroring {@link ProfileRepositoryPort}'s
 * pattern — the application layer depends only on this interface.
 */
public interface LikeRepositoryPort {

    /** Inserts the like, or overwrites the existing like for the same (liker, liked) pair. */
    Like save(Like like);

    void deleteByLikerAndLiked(ProfileId liker, ProfileId liked);

    java.util.Optional<Like> findByLikerAndLiked(ProfileId liker, ProfileId liked);

    /** Used both for idempotency (has {@code liker} already liked {@code liked}?) and reverse-like/mutual-match checks. */
    boolean existsByLikerAndLiked(ProfileId liker, ProfileId liked);

    /** All likes where {@code likedProfileId} is the liked side, most recent first. */
    List<Like> findByLikedProfileId(ProfileId likedProfileId);

    /** Removes every like where {@code profileId} is either the liker or the liked side (account deletion). */
    void deleteAllInvolvingProfile(ProfileId profileId);
}
