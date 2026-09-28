package ua.kpi.project.compatme.adapter.telegram;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStateStore;
import ua.kpi.project.compatme.application.port.out.ReverseGeocodingPort;

/**
 * Thin Telegram bot client. Deliberately contains no domain/business logic — every user action is
 * translated into a call against this backend's own REST API via {@link BackendApiClient}, per
 * the requirement that the bot interface be a separate client layer, not embedded in
 * domain/service logic.
 *
 * <p>This class only unpacks the raw Telegram {@link Update} (text message, location message, or
 * inline-keyboard callback query) and delegates to {@link ConversationFlowHandler}, which drives
 * the actual button-based onboarding conversation and settings/deletion sub-flow. Keeping that
 * logic in a separate, plain class (constructed with primitive-typed handler methods) is what
 * lets the conversation state machine be unit-tested without a real Telegram connection.
 */
public class CompatmeTelegramBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final ConversationFlowHandler flowHandler;

    public CompatmeTelegramBot(
            String botToken,
            String botUsername,
            BackendApiClient backendApiClient,
            ReverseGeocodingPort reverseGeocodingPort,
            ConversationStateStore stateStore) {
        super(botToken);
        this.botUsername = botUsername;
        this.flowHandler = new ConversationFlowHandler(this, backendApiClient, reverseGeocodingPort, stateStore);
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }
        if (!update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        long chatId = message.getChatId();
        String telegramUserId = String.valueOf(chatId);

        if (message.hasLocation()) {
            flowHandler.onLocationMessage(chatId, telegramUserId, message.getLocation().getLatitude(), message.getLocation().getLongitude());
            return;
        }
        if (message.hasPhoto()) {
            flowHandler.onPhotoMessage(chatId, telegramUserId, highestResolutionFileId(message));
            return;
        }
        if (!message.hasText()) {
            return;
        }

        String text = message.getText().trim();
        if (text.startsWith("/start")) {
            flowHandler.onStartCommand(chatId, telegramUserId);
        } else if (text.startsWith("/settings")) {
            flowHandler.onSettingsCommand(chatId, telegramUserId);
        } else if (text.startsWith("/menu")) {
            flowHandler.onMenuCommand(chatId, telegramUserId);
        } else {
            flowHandler.onTextMessage(chatId, telegramUserId, text);
        }
    }

    /** Telegram sends each photo as several {@code PhotoSize}s; picks the highest-resolution one to store. */
    private String highestResolutionFileId(Message message) {
        return message.getPhoto().stream()
                .max(java.util.Comparator.comparingInt(p -> p.getWidth() * p.getHeight()))
                .map(org.telegram.telegrambots.meta.api.objects.PhotoSize::getFileId)
                .orElse(null);
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        long chatId = callbackQuery.getMessage().getChatId();
        String telegramUserId = String.valueOf(chatId);
        Integer messageId = callbackQuery.getMessage().getMessageId();
        flowHandler.onCallbackQuery(chatId, telegramUserId, messageId, callbackQuery.getId(), callbackQuery.getData());
    }
}
