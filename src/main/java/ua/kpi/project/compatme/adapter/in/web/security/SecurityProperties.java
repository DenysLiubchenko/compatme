package ua.kpi.project.compatme.adapter.in.web.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Externalized security configuration. Secrets have no defaults on purpose: a missing or weak
 * secret fails application startup instead of silently running unprotected.
 */
@Validated
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    /** HMAC-SHA256 signing key for user JWTs (at least 32 characters). */
    @NotBlank(message = "security.jwt-secret (SECURITY_JWT_SECRET) must be set")
    @Size(min = 32, message = "security.jwt-secret must be at least 32 characters")
    private String jwtSecret;

    private Duration jwtTtl = Duration.ofMinutes(30);

    /** Shared secret for trusted service-to-service calls (the Telegram bot), sent as X-Service-Key. */
    @NotBlank(message = "security.service-key (SECURITY_SERVICE_KEY) must be set")
    @Size(min = 16, message = "security.service-key must be at least 16 characters")
    private String serviceKey;

    private Duration initDataMaxAge = Duration.ofHours(1);

    private List<String> allowedOrigins = List.of();

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public Duration getJwtTtl() {
        return jwtTtl;
    }

    public void setJwtTtl(Duration jwtTtl) {
        this.jwtTtl = jwtTtl;
    }

    public String getServiceKey() {
        return serviceKey;
    }

    public void setServiceKey(String serviceKey) {
        this.serviceKey = serviceKey;
    }

    public Duration getInitDataMaxAge() {
        return initDataMaxAge;
    }

    public void setInitDataMaxAge(Duration initDataMaxAge) {
        this.initDataMaxAge = initDataMaxAge;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins;
    }
}
