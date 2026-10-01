package ua.kpi.project.compatme.adapter.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ua.kpi.project.compatme.application.port.out.LikeNotificationPort;
import ua.kpi.project.compatme.domain.model.Profile;

import java.util.Optional;
import java.util.regex.Pattern;

/** Delivers like and mutual-match notifications through the running Telegram bot. */
@Component
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
public class TelegramLikeNotificationAdapter implements LikeNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(TelegramLikeNotificationAdapter.class);
    private static final Pattern TELEGRAM_USER_ID = Pattern.compile("[0-9]+");

    private final CompatmeTelegramBot bot;

    public TelegramLikeNotificationAdapter(CompatmeTelegramBot bot) {
        this.bot = bot;
    }

    @Override
    public void notifyNewLike(Profile recipient, Profile liker) {
        send(recipient, "Someone likes you! Open CompatMe to see who and like them back.");
    }

    @Override
    public void notifyMutualMatch(Profile first, Profile second) {
        sendMatch(first, second);
        sendMatch(second, first);
    }

    private void sendMatch(Profile recipient, Profile other) {
        if (recipient.telegramUserId() == null || recipient.telegramUserId().isBlank()) return;
        String text = "It's a mutual match with " + other.displayName() + "! Open their Telegram account to connect.";
        Optional<String> link = telegramUserLink(other.telegramUserId());
        try {
            if (link.isPresent()) {
                bot.sendNotification(recipient.telegramUserId(), text, "Open Telegram account", link.get());
            } else {
                bot.sendNotification(recipient.telegramUserId(),
                        "It's a mutual match with " + other.displayName()
                                + "! Their Telegram account link is unavailable.");
            }
        } catch (TelegramApiException | RuntimeException e) {
            log.warn("Failed to send match notification for profile {}: {}", recipient.id(), e.getMessage());
        }
    }

    static Optional<String> telegramUserLink(String telegramUserId) {
        if (telegramUserId == null || !TELEGRAM_USER_ID.matcher(telegramUserId).matches()) {
            return Optional.empty();
        }
        return Optional.of("tg://user?id=" + telegramUserId);
    }

    private void send(Profile recipient, String text) {
        if (recipient.telegramUserId() == null || recipient.telegramUserId().isBlank()) return;
        try {
            bot.sendNotification(recipient.telegramUserId(), text);
        } catch (TelegramApiException | RuntimeException e) {
            log.warn("Failed to send like notification for profile {}: {}", recipient.id(), e.getMessage());
        }
    }
}
