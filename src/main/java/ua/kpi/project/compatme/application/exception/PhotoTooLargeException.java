package ua.kpi.project.compatme.application.exception;

public class PhotoTooLargeException extends PhotoValidationException {

    private final long maxSizeBytes;
    private final long actualSizeBytes;

    public PhotoTooLargeException(long actualSizeBytes, long maxSizeBytes) {
        super("This photo is too large (%s) - please send one under %s."
                .formatted(humanReadable(actualSizeBytes), humanReadable(maxSizeBytes)));
        this.maxSizeBytes = maxSizeBytes;
        this.actualSizeBytes = actualSizeBytes;
    }

    public long maxSizeBytes() { return maxSizeBytes; }
    public long actualSizeBytes() { return actualSizeBytes; }

    private static String humanReadable(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1) {
            return mb == Math.floor(mb) ? (long) mb + " MB" : "%.1f MB".formatted(mb);
        }
        return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
    }
}
