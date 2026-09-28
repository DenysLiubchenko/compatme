package ua.kpi.project.compatme.domain.model;

import java.time.Instant;

/**
 * An explicit user action: {@code likerProfileId} liked {@code likedProfileId} while browsing
 * matches or "Who Liked Me". Deliberately independent of {@link CompatibilityScorer} and every
 * {@link CompatibilityAggregationStrategy} — a like is a recorded user decision, not a computed
 * prediction. A high compatibility score never implies a like happened, and a like never implies
 * (or requires) any particular score.
 */
public record Like(ProfileId likerProfileId, ProfileId likedProfileId, Instant likedAt) {
}
