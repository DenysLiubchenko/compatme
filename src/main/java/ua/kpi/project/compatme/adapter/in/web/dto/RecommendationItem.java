package ua.kpi.project.compatme.adapter.in.web.dto;

import ua.kpi.project.compatme.domain.model.AggregationStrategyType;

/**
 * A single recommended candidate in a {@link RecommendationsResponse}.
 */
public record RecommendationItem(
        String candidateId,
        String displayName,
        Integer age,
        double scoreAtoB,
        double scoreBtoA,
        double selfSelfSimilarity,
        AggregationStrategyType strategy,
        double aggregatedScore) {
}
