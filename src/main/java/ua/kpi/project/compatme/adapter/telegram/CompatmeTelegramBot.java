package ua.kpi.project.compatme.adapter.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStateStore;
import ua.kpi.project.compatme.adapter.telegram.ui.MainMenuKeyboard;
import ua.kpi.project.compatme.adapter.telegram.ui.MenuAction;
import ua.kpi.project.compatme.application.port.out.ReverseGeocodingPort;

import java.util.List;
import java.util.Optional;

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
 *
 * <p>Also performs a minimal identity check — Telegram's own {@code User.isBot()} flag — and
 * rejects any update whose sender is itself a bot account, before it ever reaches
 * {@link ConversationFlowHandler}. This is a cheap, always-available signal (no external
 * verification service needed) against automated/bot accounts creating profiles.
 */
public class CompatmeTelegramBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(CompatmeTelegramBot.class);

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

    public void sendNotification(String telegramUserId, String text) throws TelegramApiException {
        execute(SendMessage.builder().chatId(telegramUserId).text(text).build());
    }

    public void sendNotification(String telegramUserId, String text, String buttonText, String buttonUrl)
            throws TelegramApiException {
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(InlineKeyboardButton.builder().text(buttonText).url(buttonUrl).build()))
                .build();
        execute(SendMessage.builder().chatId(telegramUserId).text(text).replyMarkup(keyboard).build());
    }

    @Override
    public void onUpdateReceived(Update update) {
        log.info("Received Telegram update {} ({})", update.getUpdateId(), updateType(update));
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            if (isBotAccount(callbackQuery.getFrom())) {
                log.warn("Ignoring callback query from bot account {}", callbackQuery.getFrom().getId());
                return;
            }
            handleCallbackQuery(callbackQuery);
            return;
        }
        if (!update.hasMessage()) {
            return;
        }

        Message message = update.getMessage();
        long chatId = message.getChatId();
        String telegramUserId = String.valueOf(chatId);

        if (isBotAccount(message.getFrom())) {
            log.warn("Rejecting update from bot account {}", telegramUserId);
            rejectBotAccount(chatId);
            return;
        }

        if (message.hasLocation()) {
            flowHandler.onLocationMessage(chatId, telegramUserId, message.getLocation().getLatitude(), message.getLocation().getLongitude());
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
            Optional<MenuAction> menuAction = MainMenuKeyboard.match(text);
            if (menuAction.isPresent()) {
                flowHandler.onMenuAction(chatId, telegramUserId, menuAction.get());
            } else {
                flowHandler.onTextMessage(chatId, telegramUserId, text);
            }
        }
    }

    private String updateType(Update update) {
        if (update.hasCallbackQuery()) return "callback";
        if (!update.hasMessage()) return "other";
        Message message = update.getMessage();
        if (message.hasLocation()) return "location";
        if (message.hasText()) return "text";
        return "other-message";
    }

    /** Telegram's own {@code User.isBot()} flag — {@code null}-safe since {@code from} can theoretically be absent. */
    private boolean isBotAccount(User from) {
        return from != null && Boolean.TRUE.equals(from.getIsBot());
    }

    private void rejectBotAccount(long chatId) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId)
                    .text("Sorry, bot accounts can't create CompatMe profiles.")
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Failed to send bot-account rejection message: {}", e.getMessage());
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        long chatId = callbackQuery.getMessage().getChatId();
        String telegramUserId = String.valueOf(chatId);
        Integer messageId = callbackQuery.getMessage().getMessageId();
        flowHandler.onCallbackQuery(chatId, telegramUserId, messageId, callbackQuery.getId(), callbackQuery.getData());
    }
}
