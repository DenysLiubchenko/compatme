package ua.kpi.project.compatme.application.exception;

public class PhotoNotFoundException extends RuntimeException {

    public PhotoNotFoundException(String urn) {
        super("Photo not found: " + urn);
    }
}
