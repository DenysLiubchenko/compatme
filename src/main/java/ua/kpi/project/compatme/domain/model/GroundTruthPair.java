package ua.kpi.project.compatme.domain.model;

/**
 * A single labeled pair from the thesis evaluation's ground-truth dataset: two profile ids and
 * the reciprocal-compatibility label a human (or the synthetic dataset generator) assigned to
 * that pair. Used exclusively by the evaluation use case to measure how well each
 * {@link ua.kpi.project.compatme.domain.service.CompatibilityAggregationStrategy} reflects
 * genuine reciprocal compatibility — never consulted by the live recommendation/candidate-scoring
 * flow.
 */
public record GroundTruthPair(ProfileId profileAId, ProfileId profileBId, GroundTruthLabel expectedLabel) {
}
