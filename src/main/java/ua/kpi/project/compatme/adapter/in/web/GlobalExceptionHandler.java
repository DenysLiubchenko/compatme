package ua.kpi.project.compatme.adapter.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import ua.kpi.project.compatme.adapter.in.web.dto.ApiErrorResponse;
import ua.kpi.project.compatme.application.exception.ChatCompletionException;
import ua.kpi.project.compatme.application.exception.EmbeddingGenerationException;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.domain.exception.InvalidProfileDataException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Centralized exception translation for every REST controller in {@code adapter.in.web}. No
 * caller-facing response ever includes a raw stack trace — every handled exception is mapped to
 * a clean {@link ApiErrorResponse} with an appropriate HTTP status, while the full exception is
 * still logged server-side for debugging.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProfileNotFound(ProfileNotFoundException e, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(InvalidProfileDataException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidProfileData(InvalidProfileDataException e, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(MethodArgumentNotValidException e, WebRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException e, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException e, WebRequest request) {
        return build(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(EmbeddingGenerationException.class)
    public ResponseEntity<ApiErrorResponse> handleEmbeddingGenerationFailure(EmbeddingGenerationException e, WebRequest request) {
        log.error("Embedding generation failed", e);
        return build(HttpStatus.BAD_GATEWAY, "Embedding provider is currently unavailable. Please try again later.", request);
    }

    @ExceptionHandler(ChatCompletionException.class)
    public ResponseEntity<ApiErrorResponse> handleChatCompletionFailure(ChatCompletionException e, WebRequest request) {
        log.error("Chat completion failed", e);
        return build(HttpStatus.BAD_GATEWAY, "Chat model is currently unavailable. Please try again later.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e, WebRequest request) {
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, WebRequest request) {
        String path = request.getDescription(false).replaceFirst("^uri=", "");
        ApiErrorResponse body = new ApiErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path);
        return ResponseEntity.status(status).body(body);
    }
}
