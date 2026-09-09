package ua.kpi.project.compatme.adapter.out.gemini.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Internal DTO matching the JSON schema requested from the Gemini chat model when interpreting a
 * preference refinement message. Never exposed outside {@code adapter.out.gemini} — the
 * application layer only ever sees {@code PreferenceRefinementResult}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PreferenceRefinementLlmResponse(String updatedPreferenceDescription, String changeSummary) {
}
