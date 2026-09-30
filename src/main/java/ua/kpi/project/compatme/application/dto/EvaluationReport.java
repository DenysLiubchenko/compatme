package ua.kpi.project.compatme.application.dto;

import ua.kpi.project.compatme.domain.model.GroundTruthLabel;

import java.util.Map;

/**
 * Result of {@link ua.kpi.project.compatme.application.port.in.EvaluateCompatibilityScoringUseCase}:
 * the average aggregated compatibility score per expected ground-truth label, plus how many
 * pairs contributed to each label's average (i.e. were scorable — both profiles found and fully
 * embedded).
 */
public record EvaluationReport(
        Map<GroundTruthLabel, Double> averageScoreByLabel,
        Map<GroundTruthLabel, Integer> pairCounts) {
}
