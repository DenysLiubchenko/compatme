package ua.kpi.project.compatme.application.exception;

/**
 * Raised when the {@code EmbeddingProviderPort} adapter fails after exhausting retries.
 * Translated to HTTP 502/503 at the {@code adapter.in.web} boundary.
 */
public class EmbeddingGenerationException extends RuntimeException {

    public EmbeddingGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
