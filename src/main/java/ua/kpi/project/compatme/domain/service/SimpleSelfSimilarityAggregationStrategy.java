package ua.kpi.project.compatme.domain.service;

import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.model.DirectionalScores;

/**
 * SIMPLE aggregation mode (variant 2): symmetric similarity between the two self-descriptions
 * only, ignoring what either side says they're looking for. Useful as a "how alike are these two
 * people" baseline, distinct from reciprocal-interest matching.
 */
public final class SimpleSelfSimilarityAggregationStrategy implements CompatibilityAggregationStrategy {

    @Override
    public AggregationStrategyType type() {
        return AggregationStrategyType.SIMPLE_SELF_SIMILARITY;
    }

    @Override
    public double aggregate(DirectionalScores scores) {
        return scores.selfSelfSimilarity();
    }
}
