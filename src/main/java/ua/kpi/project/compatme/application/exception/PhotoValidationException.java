package ua.kpi.project.compatme.application.exception;

/** Base class for photo upload rule violations; each subclass carries a user-presentable message. */
public abstract class PhotoValidationException extends RuntimeException {

    protected PhotoValidationException(String message) {
        super(message);
    }
}
