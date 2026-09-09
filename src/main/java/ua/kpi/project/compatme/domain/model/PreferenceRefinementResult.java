package ua.kpi.project.compatme.domain.model;

/**
 * The outcome of interpreting a natural-language preference refinement message
 * (e.g. "I want someone calmer", "less about sports") via the chat model.
 *
 * @param updatedPreferenceDescription the rewritten "who I'm looking for" free text
 * @param changeSummary a short human-readable explanation of what changed, for logging/audit
 */
public record PreferenceRefinementResult(String updatedPreferenceDescription, String changeSummary) {
}
