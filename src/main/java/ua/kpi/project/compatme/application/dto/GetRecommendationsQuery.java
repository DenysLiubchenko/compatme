package ua.kpi.project.compatme.application.dto;

import ua.kpi.project.compatme.domain.model.AggregationStrategyType;

/**
 * Query for fetching top-N recommendations for a given profile under a selected aggregation
 * strategy — the strategy is selectable per-request to support the thesis's A/B evaluation.
 */
public record GetRecommendationsQuery(String requesterId, AggregationStrategyType strategy, int topN) {

    public GetRecommendationsQuery {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive, was: " + topN);
        }
    }
}
