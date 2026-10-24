package ua.kpi.project.compatme.adapter.in.web.security;

/** Thrown when Telegram Mini App {@code initData} is missing, malformed, forged or expired. */
public class InvalidTelegramInitDataException extends RuntimeException {

    public InvalidTelegramInitDataException(String message) {
        super(message);
    }
}
