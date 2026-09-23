package ua.kpi.project.compatme.adapter.in.web.dto;

import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.model.GroundTruthLabel;

import java.util.Map;

/**
 * Outbound response DTO for the thesis evaluation report. Enum map keys are serialized as their
 * {@code name()} by Jackson by default, producing the {@code {"MUTUAL_MATCH": {...}}} shape.
 */
public record EvaluationReportResponse(
        Map<GroundTruthLabel, Map<AggregationStrategyType, Double>> resultsByLabelAndStrategy,
        Map<GroundTruthLabel, Integer> pairCounts) {
}
