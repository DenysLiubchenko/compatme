package ua.kpi.project.compatme.application.port.out;

import ua.kpi.project.compatme.domain.model.OptionalProfileFields;

/** Extracts only explicitly stated optional attributes from the two profile descriptions. */
public interface ProfileAttributeExtractionPort {

    OptionalProfileFields extract(String selfDescription, String preferenceDescription);
}
