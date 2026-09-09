package ua.kpi.project.compatme.application.port.in;

import ua.kpi.project.compatme.application.dto.RefinePreferenceCommand;
import ua.kpi.project.compatme.application.dto.RefinePreferenceResult;

/**
 * Inbound use case (also referred to as {@code UpdatePreferencesUseCase} in the thesis spec) for
 * interpreting a natural-language preference refinement message, updating the profile's
 * {@code preferenceDescription}, re-embedding only the changed field, and returning refreshed
 * recommendations.
 */
public interface PreferenceRefinementUseCase {

    RefinePreferenceResult refine(RefinePreferenceCommand command);
}
