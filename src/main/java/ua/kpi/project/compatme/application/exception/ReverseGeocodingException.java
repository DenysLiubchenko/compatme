package ua.kpi.project.compatme.application.exception;

/**
 * Raised when the {@code ReverseGeocodingPort} adapter fails to resolve coordinates to a
 * country/city (network error, no result found, rate-limited, etc.). Callers (the Telegram
 * onboarding flow) are expected to catch this and fall back to manual location entry — it is not
 * translated at any REST boundary since this port has no HTTP-facing caller.
 */
public class ReverseGeocodingException extends RuntimeException {

    public ReverseGeocodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
