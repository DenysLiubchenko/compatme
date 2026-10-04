package ua.kpi.project.compatme.adapter.telegram.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import ua.kpi.project.compatme.adapter.telegram.BackendApiClient;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationState;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStateStore;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStep;
import ua.kpi.project.compatme.adapter.telegram.ui.Keyboards;
import ua.kpi.project.compatme.adapter.telegram.ui.ProfileCardFormatter;
import ua.kpi.project.compatme.adapter.telegram.ui.TelegramSender;

import java.util.List;
import java.util.Map;

/** "⚙️ Preferences": natural-language preference refinement plus shortcuts to scope / age range. */
public class PreferencesFlow {

    private static final Logger log = LoggerFactory.getLogger(PreferencesFlow.class);
    private static final int DEFAULT_TOP_N = 5;

    private final TelegramSender telegram;
    private final BackendApiClient backendApiClient;
    private final ConversationStateStore stateStore;

    public PreferencesFlow(TelegramSender telegram, BackendApiClient backendApiClient, ConversationStateStore stateStore) {
        this.telegram = telegram;
        this.backendApiClient = backendApiClient;
        this.stateStore = stateStore;
    }

    public void showMenu(long chatId) {
        telegram.sendNew(chatId,
                "⚙️ Preferences — tune who you see. You can also just send me a message describing what you want "
                        + "(e.g. \"someone calmer\") at any time.",
                Keyboards.preferences());
    }

    /** @return {@code true} if {@code data} belonged to this flow. */
    public boolean onCallback(long chatId, String telegramUserId, String callbackQueryId, String data) {
        switch (data) {
            case "post:matches" -> { telegram.ackSilently(callbackQueryId); handleShowMatches(chatId, telegramUserId); }
            case "post:prefs" -> { telegram.ackSilently(callbackQueryId); handleAdjustPreferences(chatId, telegramUserId); }
            default -> { return false; }
        }
        return true;
    }

    private void handleShowMatches(long chatId, String telegramUserId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            String profileId = String.valueOf(profile.get("id"));
            List<Map<String, Object>> recommendations = backendApiClient.getRecommendations(profileId, DEFAULT_TOP_N);
            telegram.sendText(chatId, ProfileCardFormatter.formatRecommendations(recommendations));
        } catch (Exception e) {
            log.warn("Failed to fetch matches for chat {}: {}", chatId, e.getMessage());
            telegram.sendText(chatId, "Couldn't fetch matches right now. Try again shortly.");
        }
    }

    private void handleAdjustPreferences(long chatId, String telegramUserId) {
        // Make sure the next free-text message is interpreted as a refinement request.
        ConversationState state = stateStore.loadOrCreate(telegramUserId);
        state.setStep(ConversationStep.DONE);
        stateStore.save(state);
        telegram.send(SendMessage.builder().chatId(chatId)
                .text("Send a message describing how you'd like to adjust your preferences (e.g. \"I want someone calmer\").")
                .build());
    }

    /** Free text from an onboarded, idle user: refine the preference description. */
    public void onFreeText(long chatId, String telegramUserId, String text) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            String profileId = String.valueOf(profile.get("id"));
            Map<String, Object> result = backendApiClient.refinePreference(profileId, text, DEFAULT_TOP_N);
            StringBuilder response = new StringBuilder();
            response.append("Updated: ").append(result.getOrDefault("changeSummary", "")).append("\n\n");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recommendations =
                    (List<Map<String, Object>>) result.getOrDefault("recommendations", List.of());
            response.append(ProfileCardFormatter.formatRecommendations(recommendations));
            telegram.sendText(chatId, response.toString());
        } catch (Exception e) {
            log.warn("Failed to handle free-text refinement for chat {}: {}", chatId, e.getMessage());
            telegram.sendText(chatId, "Something went wrong. Please try again later.");
        }
    }
}
