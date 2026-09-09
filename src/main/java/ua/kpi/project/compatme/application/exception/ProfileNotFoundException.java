package ua.kpi.project.compatme.application.exception;

/**
 * Raised when a requested profile does not exist. Translated to HTTP 404 at the
 * {@code adapter.in.web} boundary via the global exception handler.
 */
public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(String profileId) {
        super("Profile not found: " + profileId);
    }
}
