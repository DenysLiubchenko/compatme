package ua.kpi.project.compatme.domain.exception;

/**
 * Raised when a {@code Profile} is constructed or mutated with data that violates a core domain
 * invariant (e.g. blank description text). Framework-agnostic — translated to an HTTP response
 * only at the {@code adapter.in.web} boundary.
 */
public class InvalidProfileDataException extends RuntimeException {

    public InvalidProfileDataException(String message) {
        super(message);
    }
}
