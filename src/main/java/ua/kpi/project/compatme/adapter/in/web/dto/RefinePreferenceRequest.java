package ua.kpi.project.compatme.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.model.LocationScope;

/**
 * Inbound request DTO for submitting a natural-language preference refinement message (e.g.
 * "I want someone calmer"). {@code strategy}, {@code topN}, and {@code scope} control the
 * recommendation re-ranking returned alongside the refinement.
 */
public record RefinePreferenceRequest(
        @NotBlank(message = "message must not be blank")
        String message,

        AggregationStrategyType strategy,

        @Positive(message = "topN must be positive")
        Integer topN,

        LocationScope scope) {

    public AggregationStrategyType strategyOrDefault() {
        return strategy != null ? strategy : AggregationStrategyType.RECIPROCAL_HARMONIC;
    }

    public int topNOrDefault() {
        return topN != null ? topN : 10;
    }

    public LocationScope scopeOrDefault() {
        return scope != null ? scope : LocationScope.GLOBAL;
    }
}
