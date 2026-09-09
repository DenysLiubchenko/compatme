package ua.kpi.project.compatme.adapter.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Thin Telegram bot client. Deliberately contains no domain/business logic — every user action is
 * translated into a call against this backend's own REST API via {@link BackendApiClient}, per
 * the requirement that the bot interface be a separate client layer, not embedded in
 * domain/service logic.
 *
 * <p>Minimal conversational flow for this thesis prototype:
 * <ul>
 *   <li>{@code /start <selfDescription> | <preferenceDescription>} — creates/updates the profile</li>
 *   <li>{@code /recommend} — fetches top-5 recommendations under the reciprocal strategy</li>
 *   <li>any other free-text message — treated as a natural-language preference refinement</li>
 * </ul>
 */
public class CompatmeTelegramBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(CompatmeTelegramBot.class);
    private static final int DEFAULT_TOP_N = 5;
    private static final String DEFAULT_STRATEGY = "RECIPROCAL_HARMONIC";

    private final String botUsername;
    private final BackendApiClient backendApiClient;

    public CompatmeTelegramBot(String botToken, String botUsername, BackendApiClient backendApiClient) {
        super(botToken);
        this.botUsername = botUsername;
        this.backendApiClient = backendApiClient;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        Message message = update.getMessage();
        String chatId = String.valueOf(message.getChatId());
        String text = message.getText().trim();

        try {
            if (text.startsWith("/start")) {
                handleStart(chatId, message, text);
            } else if (text.startsWith("/recommend")) {
                handleRecommend(chatId);
            } else {
                handleRefinement(chatId, text);
            }
        } catch (Exception e) {
            log.warn("Failed to handle Telegram update for chat {}: {}", chatId, e.getMessage());
            reply(chatId, "Виникла помилка під час обробки запиту. Спробуйте пізніше.");
        }
    }

    private void handleStart(String chatId, Message message, String text) {
        String[] parts = text.replaceFirst("^/start\\s*", "").split("\\|", 2);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            reply(chatId, "Будь ласка, надішліть: /start <опис себе> | <опис бажаного партнера>");
            return;
        }
        String displayName = message.getFrom() != null && message.getFrom().getFirstName() != null
                ? message.getFrom().getFirstName()
                : "Користувач";
        backendApiClient.createOrUpdateProfile(chatId, displayName, parts[0].trim(), parts[1].trim());
        reply(chatId, "Профіль створено/оновлено! Напишіть /recommend, щоб отримати рекомендації.");
    }

    private void handleRecommend(String chatId) {
        List<Map<String, Object>> recommendations = backendApiClient.getRecommendations(chatId, DEFAULT_STRATEGY, DEFAULT_TOP_N);
        reply(chatId, formatRecommendations(recommendations));
    }

    private void handleRefinement(String chatId, String text) {
        Map<String, Object> result = backendApiClient.refinePreference(chatId, text, DEFAULT_STRATEGY, DEFAULT_TOP_N);
        StringBuilder response = new StringBuilder();
        response.append("Оновлено: ").append(result.getOrDefault("changeSummary", "")).append("\n\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) result.getOrDefault("recommendations", List.of());
        response.append(formatRecommendations(recommendations));
        reply(chatId, response.toString());
    }

    private String formatRecommendations(List<Map<String, Object>> recommendations) {
        if (recommendations.isEmpty()) {
            return "Наразі немає рекомендацій.";
        }
        return recommendations.stream()
                .map(r -> "• %s (score: %.2f)".formatted(r.get("displayName"), ((Number) r.get("aggregatedScore")).doubleValue()))
                .collect(Collectors.joining("\n"));
    }

    private void reply(String chatId, String text) {
        try {
            execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            log.warn("Failed to send Telegram reply to chat {}: {}", chatId, e.getMessage());
        }
    }
}
