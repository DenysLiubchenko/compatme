package ua.kpi.project.compatme.application.dto;

import ua.kpi.project.compatme.domain.model.LocationScope;

/**
 * Query for fetching top-N recommendations for a given profile, scored via the app's single
 * compatibility-scoring method (reciprocal harmonic mean — see {@code CompatibilityScorer}).
 *
 * @param locationScope how broadly to scope the candidate pool geographically, relative to the
 *     requester's own location; see {@link LocationScope}. May be {@code null}, in which case
 *     the requester's stored default scope is used.
 */
public record GetRecommendationsQuery(String requesterId, int topN, LocationScope locationScope) {

    public GetRecommendationsQuery {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive, was: " + topN);
        }
    }

    /** No explicit scope: the requester's stored default {@code searchScope} is used. */
    public GetRecommendationsQuery(String requesterId, int topN) {
        this(requesterId, topN, null);
    }
}
