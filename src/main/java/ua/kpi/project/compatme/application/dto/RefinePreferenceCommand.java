package ua.kpi.project.compatme.application.dto;

import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.model.LocationScope;

/**
 * Command for submitting a natural-language preference refinement message
 * (e.g. "I want someone calmer", "less about sports") and re-ranking recommendations afterward.
 */
public record RefinePreferenceCommand(
        String requesterId, String message, AggregationStrategyType strategy, int topN, LocationScope locationScope) {

    public RefinePreferenceCommand {
        if (topN <= 0) {
            throw new IllegalArgumentException("topN must be positive, was: " + topN);
        }
        if (locationScope == null) {
            locationScope = LocationScope.GLOBAL;
        }
    }
}
