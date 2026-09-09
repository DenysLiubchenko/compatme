package ua.kpi.project.compatme.domain.model;

/**
 * Identifies the available {@code CompatibilityAggregationStrategy} implementations.
 * Selectable per-request (e.g. as a query parameter) to support the thesis evaluation
 * chapter's A/B comparison between aggregation modes.
 */
public enum AggregationStrategyType {

    /** Plain average of the two directional preference-to-self scores. */
    SIMPLE_AVERAGE,

    /** Symmetric similarity between the two self-descriptions only (ignores stated preferences). */
    SIMPLE_SELF_SIMILARITY,

    /** Harmonic mean of the two directional scores; penalizes one-sided interest heavily. */
    RECIPROCAL_HARMONIC
}
