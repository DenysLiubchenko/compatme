package ua.kpi.project.compatme.adapter.in.web.dto;

import ua.kpi.project.compatme.domain.model.GroundTruthLabel;

import java.util.Map;

/**
 * Outbound response DTO for the thesis evaluation report. Enum map keys are serialized as their
 * {@code name()} by Jackson by default, producing the {@code {"MUTUAL_MATCH": 0.81}} shape.
 */
public record EvaluationReportResponse(
        Map<GroundTruthLabel, Double> averageScoreByLabel,
        Map<GroundTruthLabel, Integer> pairCounts) {
}
