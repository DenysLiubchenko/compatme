package ua.kpi.project.compatme.application.port.out;

import ua.kpi.project.compatme.domain.model.PreferenceRefinementResult;

/**
 * Outbound port for chat/generation model calls. Implemented by {@code adapter.out.gemini} today
 * (backed by a Gemini Flash model via the Google Gen AI SDK). Used to interpret a user's
 * natural-language preference refinement message ("I want someone calmer") into an updated
 * {@code preferenceDescription}.
 */
public interface ChatCompletionPort {

    /**
     * Interprets a natural-language refinement message against the user's current preference
     * description and returns a rewritten preference description plus a short change summary.
     *
     * @param currentPreferenceDescription the user's existing "who I'm looking for" text
     * @param userMessage the free-text refinement request, in the user's own language (e.g. Ukrainian)
     */
    PreferenceRefinementResult interpretPreferenceRefinement(String currentPreferenceDescription, String userMessage);
}
