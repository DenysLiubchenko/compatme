package ua.kpi.project.compatme.adapter.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ua.kpi.project.compatme.adapter.telegram.flow.BrowsingFlow;
import ua.kpi.project.compatme.adapter.telegram.flow.MenuFlow;
import ua.kpi.project.compatme.adapter.telegram.flow.OnboardingFlow;
import ua.kpi.project.compatme.adapter.telegram.flow.PreferencesFlow;
import ua.kpi.project.compatme.adapter.telegram.flow.SettingsFlow;
import ua.kpi.project.compatme.adapter.telegram.session.BrowsingSessionStore;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationState;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStateStore;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStep;
import ua.kpi.project.compatme.adapter.telegram.ui.CardMessenger;
import ua.kpi.project.compatme.adapter.telegram.ui.MenuAction;
import ua.kpi.project.compatme.adapter.telegram.ui.ReplyButtons;
import ua.kpi.project.compatme.adapter.telegram.ui.TelegramSender;
import ua.kpi.project.compatme.application.port.out.ReverseGeocodingPort;

/**
 * Thin router of the Telegram conversation. Every incoming update (free text, persistent-menu
 * button, location share, or inline-button callback) is dispatched to the flow that owns it:
 * {@link OnboardingFlow}, {@link BrowsingFlow}, {@link SettingsFlow}, {@link PreferencesFlow} or
 * {@link MenuFlow}.
 *
 * <p>Contains no domain/business logic — flows only collect input and delegate to the backend
 * REST API via {@link BackendApiClient}. Handler methods take primitive arguments rather than raw
 * Telegram {@code Update} objects so the state machine can be unit-tested without a Telegram
 * connection; {@link CompatmeTelegramBot} is the only place that unpacks an {@code Update}.
 */
public class ConversationFlowHandler {

    private static final Logger log = LoggerFactory.getLogger(ConversationFlowHandler.class);

    private final TelegramSender telegram;
    private final ConversationStateStore stateStore;

    private final BrowsingFlow browsingFlow;
    private final MenuFlow menuFlow;
    private final OnboardingFlow onboardingFlow;
    private final SettingsFlow settingsFlow;
    private final PreferencesFlow preferencesFlow;

    public ConversationFlowHandler(
            AbsSender sender,
            BackendApiClient backendApiClient,
            ReverseGeocodingPort reverseGeocodingPort,
            ConversationStateStore stateStore) {
        this.telegram = new TelegramSender(sender);
        this.stateStore = stateStore;

        BrowsingSessionStore sessions = new BrowsingSessionStore();
        CardMessenger cards = new CardMessenger(telegram);
        this.browsingFlow = new BrowsingFlow(telegram, backendApiClient, sessions, cards);
        this.menuFlow = new MenuFlow(telegram, backendApiClient, browsingFlow, sessions);
        this.onboardingFlow = new OnboardingFlow(telegram, backendApiClient, reverseGeocodingPort, stateStore, menuFlow);
        this.settingsFlow = new SettingsFlow(telegram, backendApiClient, stateStore, sessions, onboardingFlow, menuFlow);
        this.preferencesFlow = new PreferencesFlow(telegram, backendApiClient, stateStore, menuFlow);
    }

    // ─────────────────────────────── entry points ───────────────────────────────

    public void onStartCommand(long chatId, String telegramUserId) {
        if (menuFlow.hasExistingProfile(telegramUserId)) {
            leaveForms(telegramUserId);
            menuFlow.sendMainMenu(chatId, telegramUserId);
            return;
        }
        onboardingFlow.sendWelcome(chatId, telegramUserId);
    }

    /** {@code /menu} — leaves any form in progress and shows the main menu; requires an existing profile. */
    public void onMenuCommand(long chatId, String telegramUserId) {
        if (!menuFlow.hasExistingProfile(telegramUserId)) {
            telegram.sendText(chatId, "You don't have a profile yet. Send /start to create one.");
            return;
        }
        leaveForms(telegramUserId);
        menuFlow.sendMainMenu(chatId, telegramUserId);
    }

    public void onSettingsCommand(long chatId, String telegramUserId) {
        settingsFlow.handleSettingsEditProfile(chatId, telegramUserId);
    }

    /** A tap on the persistent bottom menu (delivered by Telegram as a plain text message). */
    public void onMenuAction(long chatId, String telegramUserId, MenuAction action) {
        ConversationState state = stateStore.loadOrCreate(telegramUserId);
        if (MenuFlow.isInFormStep(state.step())) {
            telegram.sendText(chatId, "Please finish the current step first, or send /menu to leave it.");
            return;
        }
        if (!menuFlow.hasExistingProfile(telegramUserId)) {
            telegram.sendText(chatId, "You don't have a profile yet. Send /start to create one.");
            return;
        }
        menuFlow.markMenuAttached(telegramUserId);
        if (state.step() != ConversationStep.DONE) {
            state.setStep(ConversationStep.DONE);
            stateStore.save(state);
        }
        switch (action) {
            case BROWSE -> browsingFlow.resumeOrStartMatches(chatId, telegramUserId);
            case MY_PROFILE -> menuFlow.showOwnProfile(chatId, telegramUserId);
            case WHO_LIKED_ME -> browsingFlow.startLikedMe(chatId, telegramUserId, null);
            case HELP -> menuFlow.sendHelp(chatId);
        }
    }

    /**
     * Free text. Reply-keyboard buttons of the active menu/step arrive here as plain text, so the
     * label is resolved to an action first; anything else is form input or preference refinement.
     */
    public void onTextMessage(long chatId, String telegramUserId, String text) {
        ConversationState state = stateStore.loadOrCreate(telegramUserId);
        var action = ReplyButtons.resolve(text, state.step());
        if (action.isPresent()) {
            try {
                if (dispatch(chatId, telegramUserId, null, null, action.get(), state)) {
                    return;
                }
            } catch (Exception e) {
                log.warn("Failed to handle button '{}' for chat {}: {}", text, chatId, e.getMessage());
                telegram.sendText(chatId, "Something went wrong. Please try /start again.");
                return;
            }
        }
        menuFlow.ensureMenuAttached(chatId, telegramUserId, state.step());
        if (onboardingFlow.onText(chatId, state, text) || settingsFlow.onText(chatId, state, text)) {
            return;
        }
        boolean idle = state.step() == ConversationStep.DONE
                || (state.step() == ConversationStep.WELCOME && menuFlow.hasExistingProfile(telegramUserId));
        if (idle) {
            preferencesFlow.onFreeText(chatId, telegramUserId, text);
            return;
        }
        telegram.sendText(chatId, "Please use the buttons below, or send /start to begin.");
    }

    public void onLocationMessage(long chatId, String telegramUserId, double latitude, double longitude) {
        onboardingFlow.onLocationMessage(chatId, telegramUserId, latitude, longitude);
    }

    /**
     * Inline-button taps. Only the Like/Skip card buttons are inline now; the other branches stay so
     * buttons on messages sent before the move to reply keyboards keep working.
     */
    public void onCallbackQuery(long chatId, String telegramUserId, Integer messageId, String callbackQueryId, String callbackData) {
        ConversationState state = stateStore.loadOrCreate(telegramUserId);
        try {
            if (!dispatch(chatId, telegramUserId, messageId, callbackQueryId, callbackData, state)) {
                log.warn("Unrecognized callback data: {}", callbackData);
                telegram.answerCallback(callbackQueryId, null, false);
            }
        } catch (Exception e) {
            log.warn("Failed to handle callback '{}' for chat {}: {}", callbackData, chatId, e.getMessage());
            telegram.answerCallback(callbackQueryId, "Something went wrong. Please try /start again.", true);
        }
    }

    /** Routes an action id (inline callback data, or a resolved reply-button label) to its flow. */
    private boolean dispatch(long chatId, String telegramUserId, Integer messageId, String callbackQueryId,
                             String action, ConversationState state) {
        return onboardingFlow.onCallback(chatId, telegramUserId, messageId, callbackQueryId, action, state)
                || settingsFlow.onCallback(chatId, telegramUserId, messageId, callbackQueryId, action, state)
                || browsingFlow.onCallback(chatId, telegramUserId, messageId, callbackQueryId, action)
                || preferencesFlow.onCallback(chatId, telegramUserId, callbackQueryId, action)
                || onLegacyMenuCallback(chatId, telegramUserId, callbackQueryId, action);
    }

    /** The inline main menu no longer exists; old ones in a chat just re-attach the bottom keyboard. */
    private boolean onLegacyMenuCallback(long chatId, String telegramUserId, String callbackQueryId, String data) {
        switch (data) {
            case "menu:back" -> { telegram.ackSilently(callbackQueryId); menuFlow.sendMainMenu(chatId, telegramUserId); }
            case "menu:profile" -> { telegram.ackSilently(callbackQueryId); menuFlow.showOwnProfile(chatId, telegramUserId); }
            default -> { return false; }
        }
        return true;
    }

    /** Abandons any in-progress form/settings step so free text is no longer consumed as form input. */
    private void leaveForms(String telegramUserId) {
        ConversationState state = stateStore.loadOrCreate(telegramUserId);
        if (state.step() != ConversationStep.DONE) {
            state.setStep(ConversationStep.DONE);
            stateStore.save(state);
        }
    }
}
