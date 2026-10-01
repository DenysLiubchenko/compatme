package ua.kpi.project.compatme.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/** Logs HTTP request method, path, status, and duration without recording payloads or query strings. */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern UUID_PATH_SEGMENT = Pattern.compile(
            "(?i)(?<=/)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?=/|$)");
    private static final Pattern NUMERIC_PATH_SEGMENT = Pattern.compile("(?<=/)\\d+(?=/|$)");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
            log.info("HTTP {} {} completed with status {} in {} ms",
                    request.getMethod(), safePath(request.getRequestURI()), response.getStatus(), durationMillis);
        }
    }

    static String safePath(String requestUri) {
        return NUMERIC_PATH_SEGMENT.matcher(UUID_PATH_SEGMENT.matcher(requestUri).replaceAll("{id}"))
                .replaceAll("{id}");
    }
}
