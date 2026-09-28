package ua.kpi.project.compatme.application.dto;

import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.model.LocationScope;

/**
 * Query for fetching top-N recommendations for a given profile under a selected aggregation
 * strategy — the strategy is selectable per-request to support the thesis's A/B evaluation.
 *
 * @param locationScope how broadly to scope the candidate pool geographically, relative to the
 *     requester's own {@code country}/{@code city}; see {@link LocationScope}.
 */
public record GetRecommendationsQuery(String requesterId, AggregationStrategyType strategy, int topN, LocationScope locationScope) {

    public GetRecommendationsQuery {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive, was: " + topN);
        }
        if (locationScope == null) {
            locationScope = LocationScope.GLOBAL;
        }
    }

    /** Defaults {@code locationScope} to {@link LocationScope#GLOBAL} for callers that don't care. */
    public GetRecommendationsQuery(String requesterId, AggregationStrategyType strategy, int topN) {
        this(requesterId, strategy, topN, LocationScope.GLOBAL);
    }
}
