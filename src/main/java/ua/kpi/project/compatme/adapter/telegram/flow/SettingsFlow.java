package ua.kpi.project.compatme.adapter.telegram.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import ua.kpi.project.compatme.adapter.telegram.BackendApiClient;
import ua.kpi.project.compatme.adapter.telegram.session.BrowsingSessionStore;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationState;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStateStore;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStep;
import ua.kpi.project.compatme.adapter.telegram.ui.ReplyKeyboards;
import ua.kpi.project.compatme.adapter.telegram.ui.Labels;
import ua.kpi.project.compatme.adapter.telegram.ui.TelegramSender;
import ua.kpi.project.compatme.adapter.telegram.validation.ProfileInputValidator;

import java.util.List;
import java.util.Map;

/** Settings sub-flow: search scope, match age range, profile editing, pause stub and account deletion. */
public class SettingsFlow {

    private static final Logger log = LoggerFactory.getLogger(SettingsFlow.class);

    private final TelegramSender telegram;
    private final BackendApiClient backendApiClient;
    private final ConversationStateStore stateStore;
    private final BrowsingSessionStore browsingSessions;
    private final OnboardingFlow onboardingFlow;
    private final MenuFlow menuFlow;

    public SettingsFlow(
            TelegramSender telegram,
            BackendApiClient backendApiClient,
            ConversationStateStore stateStore,
            BrowsingSessionStore browsingSessions,
            OnboardingFlow onboardingFlow,
            MenuFlow menuFlow) {
        this.menuFlow = menuFlow;
        this.telegram = telegram;
        this.backendApiClient = backendApiClient;
        this.stateStore = stateStore;
        this.browsingSessions = browsingSessions;
        this.onboardingFlow = onboardingFlow;
    }

    /** Free text while waiting for a new match age range. */
    public boolean onText(long chatId, ConversationState state, String text) {
        if (state.step() == ConversationStep.SETTINGS_AGE_RANGE) {
            handleSettingsAgeRangeInput(chatId, state, text);
            return true;
        }
        return false;
    }

    /** @return {@code true} if {@code data} belonged to the settings flow. */
    public boolean onCallback(long chatId, String telegramUserId, Integer messageId, String callbackQueryId,
                              String data, ConversationState state) {
        switch (data) {
            case "settings:agerange" -> { ackSilently(callbackQueryId); promptSettingsAgeRange(chatId, state); }
            case "settings:scope" -> { ackSilently(callbackQueryId); showSettingsScopeMenu(chatId, telegramUserId); }
            case "settings:edit", "profile:edit" -> { ackSilently(callbackQueryId); handleSettingsEditProfile(chatId, telegramUserId); }
            case "settings:pause" -> { ackSilently(callbackQueryId); handleSettingsPause(chatId); }
            case "settings:delete" -> { ackSilently(callbackQueryId); handleSettingsDelete(chatId, messageId, state); }
            case "delete:yes" -> { ackSilently(callbackQueryId); handleDeleteConfirmed(chatId, messageId, telegramUserId); }
            case "delete:no" -> { ackSilently(callbackQueryId); handleDeleteCancelled(chatId, messageId, state); }
            case "menu:settings" -> { ackSilently(callbackQueryId); showSettingsMenu(chatId, telegramUserId); }
            default -> {
                if (data.startsWith("setscope:")) {
                    ackSilently(callbackQueryId);
                    handleSettingsScopeChoice(chatId, messageId, telegramUserId, data.substring("setscope:".length()));
                    return true;
                }
                return false;
            }
        }
        return true;
    }

    /** {@code /settings}: account actions live in the "My Profile" menu, so open that. */
    public void showSettingsMenu(long chatId, String telegramUserId) {
        ConversationState state = stateStore.loadOrCreate(telegramUserId);
        state.setStep(ConversationStep.DONE);
        stateStore.save(state);
        menuFlow.showOwnProfile(chatId, telegramUserId);
    }

    private void showSettingsScopeMenu(long chatId, String telegramUserId) {
        ConversationState state = stateStore.loadOrCreate(telegramUserId);
        state.setStep(ConversationStep.SETTINGS_SCOPE);
        stateStore.save(state);
        telegram.sendNew(chatId, "🌍 Choose your default search scope:", ReplyKeyboards.scopeWithBack());
    }

    private void handleSettingsScopeChoice(long chatId, Integer messageId, String telegramUserId, String scope) {
        if (!Labels.isValidScope(scope)) {
            return;
        }
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            backendApiClient.updateSearchScope(String.valueOf(profile.get("id")), scope);
            telegram.editIfPresent(chatId, messageId, "Search scope updated: " + Labels.humanizeScope(scope) + " ✅");
            ConversationState state = stateStore.loadOrCreate(telegramUserId);
            state.setStep(ConversationStep.DONE);
            stateStore.save(state);
            menuFlow.showOwnProfile(chatId, telegramUserId);
        } catch (Exception e) {
            log.warn("Failed to update search scope for chat {}: {}", chatId, e.getMessage());
            send(SendMessage.builder().chatId(chatId).text("Couldn't update your search scope. Try again shortly.").build());
        }
    }

    private void promptSettingsAgeRange(long chatId, ConversationState state) {
        state.setStep(ConversationStep.SETTINGS_AGE_RANGE);
        stateStore.save(state);
        telegram.sendNew(chatId, "\ud83c\udfaf Send your new preferred match age range, like 25-35.",
                ReplyKeyboards.backOnly());
    }

    private void handleSettingsAgeRangeInput(long chatId, ConversationState state, String text) {
        var range = ProfileInputValidator.parseAgeRange(text);
        if (range.isEmpty()) {
            send(SendMessage.builder().chatId(chatId)
                    .text("Please send a range like 25-35 (ages 18-99, minimum not above maximum).").build());
            return;
        }
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(state.telegramUserId());
            backendApiClient.updateAgeRange(String.valueOf(profile.get("id")), range.get()[0], range.get()[1]);
            state.setStep(ConversationStep.DONE);
            stateStore.save(state);
            telegram.sendText(chatId, "Preferred age range updated: " + range.get()[0] + "-" + range.get()[1] + " \u2705");
            menuFlow.showOwnProfile(chatId, state.telegramUserId());
        } catch (Exception e) {
            log.warn("Failed to update age range for chat {}: {}", chatId, e.getMessage());
            send(SendMessage.builder().chatId(chatId).text("Couldn't update your age range. Try again shortly.").build());
        }
    }

    public void handleSettingsEditProfile(long chatId, String telegramUserId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            ConversationState state = fromExistingProfile(telegramUserId, profile);
            stateStore.save(state);
            onboardingFlow.showReview(chatId, state);
        } catch (Exception e) {
            log.warn("Failed to load profile for editing, chat {}: {}", chatId, e.getMessage());
            send(SendMessage.builder().chatId(chatId)
                    .text("We couldn't find an existing profile. Send /start to create one.")
                    .build());
        }
    }

    @SuppressWarnings("unchecked")
    private ConversationState fromExistingProfile(String telegramUserId, Map<String, Object> profile) {
        ConversationState state = new ConversationState(telegramUserId);
        state.setName(String.valueOf(profile.get("displayName")));
        state.setAge(((Number) profile.get("age")).intValue());
        state.setGender(String.valueOf(profile.get("gender")));
        Object orientation = profile.get("orientation");
        if (orientation != null) {
            state.setOrientation(String.valueOf(orientation));
        }
        for (Object gender : (List<Object>) profile.getOrDefault("seekingGenders", List.of())) {
            state.toggleSeekingGender(String.valueOf(gender));
        }
        state.setCountry((String) profile.get("country"));
        state.setCity((String) profile.get("city"));
        state.setSearchScope((String) profile.get("searchScope"));
        Number minAge = (Number) profile.get("minPreferredAge");
        Number maxAge = (Number) profile.get("maxPreferredAge");
        state.setPreferredAgeRange(minAge == null ? null : minAge.intValue(), maxAge == null ? null : maxAge.intValue());
        state.setSelfDescription(String.valueOf(profile.get("selfDescription")));
        state.setPreferenceDescription(String.valueOf(profile.get("preferenceDescription")));
        for (Object photoUrn : (List<Object>) profile.getOrDefault("photoUrns", List.of())) {
            state.addPhotoUrn(String.valueOf(photoUrn));
        }
        state.setProfileUpdate(true);
        return state;
    }

    private void handleSettingsPause(long chatId) {
        // No pause/deactivate concept exists in the domain model yet; kept as a placeholder menu
        // entry per the settings-menu requirement rather than adding unrequested domain scope.
        send(SendMessage.builder().chatId(chatId).text("🔕 Pause Matching is coming soon.").build());
    }

    private void handleSettingsDelete(long chatId, Integer messageId, ConversationState state) {
        state.setStep(ConversationStep.DELETE_CONFIRM);
        stateStore.save(state);
        telegram.sendNew(chatId, "⚠️ Are you sure? This cannot be undone.", ReplyKeyboards.deleteConfirm());
    }

    private void handleDeleteConfirmed(long chatId, Integer messageId, String telegramUserId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            backendApiClient.deleteProfile(String.valueOf(profile.get("id")));
        } catch (Exception e) {
            log.warn("Failed to delete profile for chat {}: {}", chatId, e.getMessage());
        }
        stateStore.clear(telegramUserId);
        browsingSessions.remove(telegramUserId);
        telegram.editIfPresent(chatId, messageId, "Your account has been deleted.");
        // Also removes the bottom menu, which is meaningless without a profile.
        telegram.sendTextRemovingKeyboard(chatId,
                "Your account has been deleted. Send /start any time to create a new profile.");
    }

    private void handleDeleteCancelled(long chatId, Integer messageId, ConversationState state) {
        state.setStep(ConversationStep.DONE);
        stateStore.save(state);
        menuFlow.showOwnProfile(chatId, state.telegramUserId());
    }

    private void send(org.telegram.telegrambots.meta.api.methods.BotApiMethod<? extends java.io.Serializable> method) {
        telegram.send(method);
    }

    private void sendNew(long chatId, String text, ReplyKeyboard keyboard) {
        telegram.sendNew(chatId, text, keyboard);
    }

    private void ackSilently(String callbackQueryId) {
        telegram.ackSilently(callbackQueryId);
    }
}
