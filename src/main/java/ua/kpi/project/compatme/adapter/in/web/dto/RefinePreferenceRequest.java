package ua.kpi.project.compatme.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import ua.kpi.project.compatme.domain.model.AggregationStrategyType;

/**
 * Inbound request DTO for submitting a natural-language preference refinement message (e.g.
 * Ukrainian: "хочу когось спокійнішого"). {@code strategy} and {@code topN} control the
 * recommendation re-ranking returned alongside the refinement.
 */
public record RefinePreferenceRequest(
        @NotBlank(message = "message must not be blank")
        String message,

        AggregationStrategyType strategy,

        @Positive(message = "topN must be positive")
        Integer topN) {

    public AggregationStrategyType strategyOrDefault() {
        return strategy != null ? strategy : AggregationStrategyType.RECIPROCAL_HARMONIC;
    }

    public int topNOrDefault() {
        return topN != null ? topN : 10;
    }
}
