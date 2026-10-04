package ua.kpi.project.compatme.application.exception;

/** Raised when the photo storage backend fails (connectivity, permissions, etc.). */
public class PhotoStorageException extends RuntimeException {

    public PhotoStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
