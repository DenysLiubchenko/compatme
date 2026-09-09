package ua.kpi.project.compatme.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Enables Spring Retry's {@code @Retryable} annotation processing, used by the Gemini adapters
 * for exponential backoff on transient failures (e.g. HTTP 429 rate limiting).
 */
@Configuration
@EnableRetry
public class RetryConfig {
}
