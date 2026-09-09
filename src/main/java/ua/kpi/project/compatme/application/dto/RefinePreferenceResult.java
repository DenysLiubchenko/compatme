package ua.kpi.project.compatme.application.dto;

import java.util.List;

/**
 * Result of {@code RefinePreferenceUseCase}: the rewritten preference description plus the
 * refreshed recommendation ranking computed against it.
 */
public record RefinePreferenceResult(
        String updatedPreferenceDescription,
        String changeSummary,
        List<RecommendationResult> recommendations) {
}
