package ua.kpi.project.compatme.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the Telegram bot client (a thin adapter that talks HTTP to this
 * backend's own REST API — see {@code adapter.telegram}). Bound from {@code application.yml}
 * under the {@code telegram} prefix.
 */
@ConfigurationProperties(prefix = "telegram")
public class TelegramBotProperties {

    /** Whether the Telegram bot client should start alongside the backend. */
    private boolean enabled = false;

    /** Bot token issued by @BotFather. Must be supplied via environment variable. */
    private String botToken;

    /** Bot username, as registered with @BotFather. */
    private String botUsername;

    /** Base URL of this backend's own REST API, e.g. {@code http://localhost:8080}. */
    private String backendBaseUrl = "http://localhost:8080";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public String getBotUsername() {
        return botUsername;
    }

    public void setBotUsername(String botUsername) {
        this.botUsername = botUsername;
    }

    public String getBackendBaseUrl() {
        return backendBaseUrl;
    }

    public void setBackendBaseUrl(String backendBaseUrl) {
        this.backendBaseUrl = backendBaseUrl;
    }
}
