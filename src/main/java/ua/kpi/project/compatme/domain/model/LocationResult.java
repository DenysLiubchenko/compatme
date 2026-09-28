package ua.kpi.project.compatme.domain.model;

/**
 * Result of resolving GPS coordinates to a human-readable location via reverse geocoding. Used
 * exclusively by the Telegram onboarding flow's "share my location" path — never consulted by
 * scoring/recommendation logic (which only ever reads {@link Profile#country()}/{@link
 * Profile#city()} once the user has confirmed and saved them).
 */
public record LocationResult(String country, String city) {
}
