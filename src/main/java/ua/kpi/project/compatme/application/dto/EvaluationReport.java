package ua.kpi.project.compatme.application.dto;

import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.model.GroundTruthLabel;

import java.util.Map;

/**
 * Result of {@link ua.kpi.project.compatme.application.port.in.EvaluateAggregationStrategiesUseCase}:
 * a 3x3 table of the average aggregated compatibility score per (expected label, aggregation
 * strategy) combination, plus how many ground-truth pairs contributed to each label's row (i.e.
 * were scorable — both profiles found and fully embedded).
 */
public record EvaluationReport(
        Map<GroundTruthLabel, Map<AggregationStrategyType, Double>> resultsByLabelAndStrategy,
        Map<GroundTruthLabel, Integer> pairCounts) {
}
