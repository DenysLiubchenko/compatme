package ua.kpi.project.compatme.application.exception;

public class TooManyPhotosException extends PhotoValidationException {

    private final int maxPhotos;

    public TooManyPhotosException(int maxPhotos) {
        super("You already have the maximum of %d photos - please delete one first.".formatted(maxPhotos));
        this.maxPhotos = maxPhotos;
    }

    public int maxPhotos() { return maxPhotos; }
}
