package ua.kpi.project.compatme.domain.service;

import ua.kpi.project.compatme.domain.model.DirectionalScores;

/**
 * Aggregates the directional (and self-self) similarity scores between two profiles into a
 * single compatibility score. {@link ReciprocalHarmonicAggregationStrategy} is the project's one
 * and only implementation — this interface remains a separate abstraction (rather than inlining
 * the formula into {@link CompatibilityScorer}) purely to keep the aggregation math independently
 * unit-testable and swappable, without implying multiple interchangeable strategies exist.
 */
public interface CompatibilityAggregationStrategy {

    /**
     * @return an aggregated compatibility score. Not strictly bounded to {@code [0, 1]} (cosine
     *     similarity can be negative), but in practice close to {@code [0, 1]} for
     *     semantically-related short profile texts.
     */
    double aggregate(DirectionalScores scores);
}
