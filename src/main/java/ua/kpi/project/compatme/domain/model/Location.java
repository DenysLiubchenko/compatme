package ua.kpi.project.compatme.domain.model;

import ua.kpi.project.compatme.domain.exception.InvalidProfileDataException;

/**
 * Structured, mandatory profile location. Both {@code city} and {@code country} must be
 * non-blank; values are stored trimmed. Pure domain value object (no framework annotations).
 */
public record Location(String city, String country) {

    public Location {
        city = requireNonBlank(city, "city");
        country = requireNonBlank(country, "country");
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidProfileDataException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
