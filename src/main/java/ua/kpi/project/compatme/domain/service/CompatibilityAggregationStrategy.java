package ua.kpi.project.compatme.domain.service;

import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.model.DirectionalScores;

/**
 * Strategy for aggregating the directional (and self-self) similarity scores between two
 * profiles into a single compatibility score.
 *
 * <p>This is the central abstraction of the thesis's evaluation chapter: the same directional
 * scores can be fed into different strategies (plain average, self-similarity only, reciprocal
 * harmonic mean) to compare how each aggregation mode ranks candidates. Implementations must
 * remain pure functions with no side effects, so they can be unit-tested in complete isolation
 * from Spring, MongoDB, or the Gemini SDK.
 */
public interface CompatibilityAggregationStrategy {

    AggregationStrategyType type();

    /**
     * @return an aggregated compatibility score. Not strictly bounded to {@code [0, 1]} for every
     *     strategy (cosine similarity can be negative), but in practice close to {@code [0, 1]}
     *     for semantically-related short profile texts.
     */
    double aggregate(DirectionalScores scores);
}
