package ua.kpi.project.compatme.application.exception;

/**
 * Raised when the {@code ChatCompletionPort} adapter fails after exhausting retries, or returns a
 * response that cannot be parsed into a {@code PreferenceRefinementResult}.
 */
public class ChatCompletionException extends RuntimeException {

    public ChatCompletionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChatCompletionException(String message) {
        super(message);
    }
}
