package ua.kpi.project.compatme.application.exception;

import java.util.List;

public class UnsupportedPhotoTypeException extends PhotoValidationException {

    private final String contentType;
    private final List<String> allowedContentTypes;

    public UnsupportedPhotoTypeException(String contentType, List<String> allowedContentTypes) {
        super("Unsupported photo type '%s'. Allowed types: %s."
                .formatted(contentType, String.join(", ", allowedContentTypes)));
        this.contentType = contentType;
        this.allowedContentTypes = List.copyOf(allowedContentTypes);
    }

    public String contentType() { return contentType; }
    public List<String> allowedContentTypes() { return allowedContentTypes; }
}
