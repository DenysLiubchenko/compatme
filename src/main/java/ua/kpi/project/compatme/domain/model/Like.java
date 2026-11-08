package ua.kpi.project.compatme.domain.model;

import java.time.Instant;

/**
 * An explicit user action: {@code likerProfileId} liked {@code likedProfileId} while browsing
 * matches or "Who Liked Me". Deliberately independent of {@link CompatibilityScorer} and every
 * {@link CompatibilityAggregationStrategy} — a like is a recorded user decision, not a computed
 * prediction. {@code answered} is set once the liked profile has liked back (a mutual match);
 * answered likes no longer appear in "Who Liked Me" until the liker likes again. A high compatibility score never implies a like happened, and a like never implies
 * (or requires) any particular score.
 */
public record Like(ProfileId likerProfileId, ProfileId likedProfileId, Instant likedAt, boolean answered) {

    /** A fresh like that the liked profile has not yet answered. */
    public Like(ProfileId likerProfileId, ProfileId likedProfileId, Instant likedAt) {
        this(likerProfileId, likedProfileId, likedAt, false);
    }

    public Like markedAnswered() {
        return new Like(likerProfileId, likedProfileId, likedAt, true);
    }

    /** The same pair liked again after having been answered: it becomes pending again. */
    public Like renewed(Instant now) {
        return new Like(likerProfileId, likedProfileId, now, false);
    }
}
