package ua.kpi.project.compatme.domain.service;

import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.model.DirectionalScores;

/**
 * SIMPLE aggregation mode (variant 1): plain arithmetic mean of the two directional scores
 * (A's preference vs B's self, and B's preference vs A's self). Included as the naive baseline
 * against which {@link ReciprocalHarmonicAggregationStrategy} is compared in the thesis
 * evaluation chapter — it does not penalize a large asymmetry between the two directions.
 */
public final class SimpleAverageAggregationStrategy implements CompatibilityAggregationStrategy {

    @Override
    public AggregationStrategyType type() {
        return AggregationStrategyType.SIMPLE_AVERAGE;
    }

    @Override
    public double aggregate(DirectionalScores scores) {
        return (scores.scoreAtoB() + scores.scoreBtoA()) / 2.0;
    }
}
