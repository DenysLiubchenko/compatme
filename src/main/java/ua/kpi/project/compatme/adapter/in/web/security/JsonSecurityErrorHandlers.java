package ua.kpi.project.compatme.adapter.in.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import ua.kpi.project.compatme.adapter.in.web.dto.ApiErrorResponse;

import java.io.IOException;
import java.time.Instant;

/** Writes 401/403 from the security filter chain in the same {@link ApiErrorResponse} shape as the REST layer. */
public final class JsonSecurityErrorHandlers {

    private JsonSecurityErrorHandlers() {
    }

    public static AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper mapper) {
        return (request, response, e) ->
                write(mapper, request, response, HttpStatus.UNAUTHORIZED, "Authentication is required.");
    }

    public static AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) {
        return (request, response, e) ->
                write(mapper, request, response, HttpStatus.FORBIDDEN, "Access denied.");
    }

    private static void write(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                              HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        mapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
