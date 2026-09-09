package ua.kpi.project.compatme.adapter.in.web.dto;

import java.time.Instant;

/**
 * Clean, uniform error response returned by {@link ua.kpi.project.compatme.adapter.in.web.GlobalExceptionHandler}
 * for every handled exception type — callers never see a raw stack trace.
 */
public record ApiErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
