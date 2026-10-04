package ua.kpi.project.compatme.adapter.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationState;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStateStore;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStep;
import ua.kpi.project.compatme.adapter.telegram.validation.ProfileInputValidator;
import ua.kpi.project.compatme.application.exception.ReverseGeocodingException;
import ua.kpi.project.compatme.application.port.out.ReverseGeocodingPort;
import ua.kpi.project.compatme.domain.model.LocationResult;
import ua.kpi.project.compatme.domain.model.Orientation;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives the button-driven onboarding/profile-creation conversation and the always-accessible
 * settings/deletion sub-flow. Routes every incoming update (free text, location share, or
 * callback-query button tap) based on the user's currently stored {@link ConversationStep}.
 *
 * <p>Contains no domain/business logic itself — the final "save profile" step maps the collected
 * {@link ConversationState} fields onto the existing {@code POST /api/v1/profiles} +
 * {@code POST /api/v1/profiles/{id}/embeddings} calls via {@link BackendApiClient}, and account
 * deletion reuses the existing {@code DELETE /api/v1/profiles/{id}} endpoint — this class only
 * collects input and delegates, matching the rest of {@code adapter.telegram}.
 *
 * <p>Deliberately exposes primitive-typed handler methods ({@code onTextMessage(chatId,
 * telegramUserId, text)}, etc.) rather than taking raw Telegram {@code Update}/{@code
 * CallbackQuery} objects, so the conversation state machine can be unit-tested without
 * constructing real Telegram API objects — {@link CompatmeTelegramBot} is the only place that
 * unpacks an {@code Update} before delegating here.
 */
public class ConversationFlowHandler {

    private static final Logger log = LoggerFactory.getLogger(ConversationFlowHandler.class);

    private static final List<String> GENDER_OPTIONS = List.of("MALE", "FEMALE", "NON_BINARY");
    private static final int DEFAULT_TOP_N = 5;
    private static final int BROWSE_FETCH_SIZE = 20;
    private static final int MAX_PHOTOS = 5;

    private final AbsSender sender;
    private final BackendApiClient backendApiClient;
    private final ReverseGeocodingPort reverseGeocodingPort;
    private final ConversationStateStore stateStore;

    /**
     * Session-scoped "My Matches"/"Who Liked Me" browsing queues, keyed by {@code telegramUserId}
     * — intentionally in-memory only (not persisted via {@link ConversationStateStore}), since a
     * browsing session doesn't need to survive a bot restart the way in-progress onboarding does.
     * Each element is the full candidate detail map as returned by the recommendations/liked-by
     * endpoints (name, age, location, descriptions, photo file ids) — fetched once per browsing
     * session, not re-fetched per card.
     */
    private final Map<String, Deque<Map<String, Object>>> browsingQueues = new ConcurrentHashMap<>();

    /** {@code "MATCHES"} or {@code "LIKED_ME"} — which action-button set the current card should show. */
    private final Map<String, String> browsingModes = new ConcurrentHashMap<>();

    /** The candidate currently on-screen for a user, so "Like"/"Skip" know who it refers to. */
    private final Map<String, Map<String, Object>> currentCandidates = new ConcurrentHashMap<>();

    public ConversationFlowHandler(
            AbsSender sender,
            BackendApiClient backendApiClient,
            ReverseGeocodingPort reverseGeocodingPort,
            ConversationStateStore stateStore) {
        this.sender = sender;
        this.backendApiClient = backendApiClient;
        this.reverseGeocodingPort = reverseGeocodingPort;
        this.stateStore = stateStore;
    }

    // ─────────────────────────────── entry points ───────────────────────────────

    public void onStartCommand(long chatId, String telegramUserId) {
        if (hasExistingProfile(telegramUserId)) {
            sendMainMenu(chatId, telegramUserId);
            return;
        }

        ConversationState state = new ConversationState(telegramUserId);
        state.setStep(ConversationStep.WELCOME);
        stateStore.save(state);

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("🚀 Create My Profile", "start_create")))
                .build();
        send(SendMessage.builder()
                .chatId(chatId)
                .text("Welcome to CompatMe! Let's build a profile that finds people who are genuinely compatible with you.")
                .replyMarkup(keyboard)
                .build());
    }

    /** {@code /menu} — same persistent main menu shown after profile creation; requires an existing profile. */
    public void onMenuCommand(long chatId, String telegramUserId) {
        if (!hasExistingProfile(telegramUserId)) {
            send(SendMessage.builder().chatId(chatId)
                    .text("You don't have a profile yet. Send /start to create one.")
                    .build());
            return;
        }
        sendMainMenu(chatId, telegramUserId);
    }

    private boolean hasExistingProfile(String telegramUserId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            return profile != null && profile.get("id") != null;
        } catch (Exception e) {
            return false;
        }
    }

    public void onSettingsCommand(long chatId, String telegramUserId) {
        showSettingsMenu(chatId, telegramUserId);
    }

    private void showSettingsMenu(long chatId, String telegramUserId) {
        ConversationState state = stateStore.loadOrCreate(telegramUserId);
        state.setStep(ConversationStep.SETTINGS_MENU);
        stateStore.save(state);
        sendNew(chatId, "⚙️ Settings", settingsKeyboard());
    }

    public void onTextMessage(long chatId, String telegramUserId, String text) {
        ConversationState state = stateStore.loadOrCreate(telegramUserId);
        switch (state.step()) {
            case NAME -> handleNameInput(chatId, state, text);
            case AGE -> handleAgeInput(chatId, state, text);
            case LOCATION_MANUAL_COUNTRY -> handleManualCountryInput(chatId, state, text);
            case LOCATION_MANUAL_CITY -> handleManualCityInput(chatId, state, text);
            case AGE_RANGE -> handleAgeRangeInput(chatId, state, text);
            case SETTINGS_AGE_RANGE -> handleSettingsAgeRangeInput(chatId, state, text);
            case SELF_DESCRIPTION -> handleSelfDescriptionInput(chatId, state, text);
            case PREFERENCE_DESCRIPTION -> handlePreferenceDescriptionInput(chatId, state, text);
            case PHOTOS -> handlePhotoUrlInput(chatId, state, text);
            case DONE -> handlePostOnboardingFreeText(chatId, telegramUserId, text);
            default -> send(SendMessage.builder().chatId(chatId)
                    .text("Please use the buttons above, or send /start to begin.")
                    .build());
        }
    }

    public void onLocationMessage(long chatId, String telegramUserId, double latitude, double longitude) {
        ConversationState state = stateStore.loadOrCreate(telegramUserId);
        if (state.step() != ConversationStep.LOCATION_SHARE_PENDING) {
            return;
        }
        send(SendMessage.builder().chatId(chatId).text("Got it, looking that up...")
                .replyMarkup(ReplyKeyboardRemove.builder().removeKeyboard(true).build())
                .build());
        try {
            LocationResult result = reverseGeocodingPort.resolveLocation(latitude, longitude);
            state.setPendingCountry(result.country());
            state.setPendingCity(result.city());
            state.setStep(ConversationStep.LOCATION_CONFIRM);
            stateStore.save(state);
            promptLocationConfirm(chatId, result);
        } catch (ReverseGeocodingException e) {
            log.warn("Reverse geocoding failed for chat {}: {}", chatId, e.getMessage());
            state.setStep(ConversationStep.LOCATION_MANUAL_COUNTRY);
            stateStore.save(state);
            send(SendMessage.builder().chatId(chatId)
                    .text("We couldn't detect your location automatically. Let's enter it manually instead.")
                    .build());
            promptManualCountry(chatId);
        }
    }

    public void onCallbackQuery(long chatId, String telegramUserId, Integer messageId, String callbackQueryId, String callbackData) {
        ConversationState state = stateStore.loadOrCreate(telegramUserId);
        try {
            switch (callbackData) {
                case "start_create" -> { ackSilently(callbackQueryId); startNameStep(chatId, messageId, state); }
                case "back" -> { ackSilently(callbackQueryId); handleBack(chatId, messageId, state); }
                case "loc:share" -> { ackSilently(callbackQueryId); handleLocationShareChoice(chatId, messageId, state); }
                case "loc:manual" -> { ackSilently(callbackQueryId); handleLocationManualChoice(chatId, messageId, state); }
                case "loc:confirm_yes" -> { ackSilently(callbackQueryId); handleLocationConfirmYes(chatId, messageId, state); }
                case "loc:confirm_no" -> { ackSilently(callbackQueryId); handleLocationConfirmNo(chatId, messageId, state); }
                case "settings:agerange" -> { ackSilently(callbackQueryId); promptSettingsAgeRange(chatId, state); }
                case "review:edit_agerange" -> { ackSilently(callbackQueryId); jumpToEditAgeRange(chatId, messageId, state); }
                case "agerange:default" -> { ackSilently(callbackQueryId); handleAgeRangeDefault(chatId, messageId, state); }
                case "settings:scope" -> { ackSilently(callbackQueryId); showSettingsScopeMenu(chatId); }
                case "review:edit_scope" -> { ackSilently(callbackQueryId); jumpToEditScope(chatId, messageId, state); }
                case "seek:continue" -> handleSeekingGendersContinue(chatId, messageId, callbackQueryId, state);
                case "review:save" -> { ackSilently(callbackQueryId); handleReviewSave(chatId, messageId, telegramUserId, state); }
                case "review:edit_name" -> { ackSilently(callbackQueryId); jumpToEdit(chatId, messageId, state, ConversationStep.NAME); }
                case "review:edit_age" -> { ackSilently(callbackQueryId); jumpToEdit(chatId, messageId, state, ConversationStep.AGE); }
                case "review:edit_gender" -> { ackSilently(callbackQueryId); jumpToEditGender(chatId, messageId, state); }
                case "review:edit_orientation" -> { ackSilently(callbackQueryId); jumpToEditOrientation(chatId, messageId, state); }
                case "review:edit_location" -> { ackSilently(callbackQueryId); jumpToEditLocation(chatId, messageId, state); }
                case "review:edit_desc" -> { ackSilently(callbackQueryId); jumpToEdit(chatId, messageId, state, ConversationStep.SELF_DESCRIPTION); }
                case "review:edit_photos" -> { ackSilently(callbackQueryId); jumpToEditPhotos(chatId, messageId, state); }
                case "photo:add", "photo:add_another" -> { ackSilently(callbackQueryId); handlePhotoAddPrompt(chatId, state); }
                case "photo:skip", "photo:done" -> { ackSilently(callbackQueryId); handlePhotosDone(chatId, state); }
                case "photo_manage:add" -> { ackSilently(callbackQueryId); handlePhotoManageAdd(chatId, state); }
                case "photo_manage:done" -> { ackSilently(callbackQueryId); showReview(chatId, state); }
                case "post:matches" -> { ackSilently(callbackQueryId); handleShowMatches(chatId, telegramUserId); }
                case "post:prefs" -> { ackSilently(callbackQueryId); handleAdjustPreferences(chatId); }
                case "settings:edit" -> { ackSilently(callbackQueryId); handleSettingsEditProfile(chatId, telegramUserId); }
                case "settings:pause" -> { ackSilently(callbackQueryId); handleSettingsPause(chatId); }
                case "settings:delete" -> { ackSilently(callbackQueryId); handleSettingsDelete(chatId, messageId, state); }
                case "delete:yes" -> { ackSilently(callbackQueryId); handleDeleteConfirmed(chatId, messageId, telegramUserId); }
                case "delete:no" -> { ackSilently(callbackQueryId); handleDeleteCancelled(chatId, messageId, state); }
                case "menu:back" -> { ackSilently(callbackQueryId); sendMainMenu(chatId, telegramUserId); }
                case "menu:profile" -> { ackSilently(callbackQueryId); handleMyProfile(chatId, telegramUserId); }
                case "menu:matches" -> { ackSilently(callbackQueryId); handleMyMatches(chatId, telegramUserId); }
                case "menu:liked" -> { ackSilently(callbackQueryId); handleWhoLikedMe(chatId, telegramUserId); }
                case "menu:settings" -> { ackSilently(callbackQueryId); showSettingsMenu(chatId, telegramUserId); }
                case "profile:edit" -> { ackSilently(callbackQueryId); handleSettingsEditProfile(chatId, telegramUserId); }
                case "browse:like" -> { ackSilently(callbackQueryId); handleBrowseLike(chatId, telegramUserId); }
                case "browse:likeback" -> { ackSilently(callbackQueryId); handleBrowseLike(chatId, telegramUserId); }
                case "browse:skip", "browse:next" -> { ackSilently(callbackQueryId); showNextBrowsingCard(chatId, telegramUserId); }
                default -> handleDynamicOrGenderOrSeekingCallback(chatId, messageId, callbackQueryId, state, callbackData);
            }
        } catch (Exception e) {
            log.warn("Failed to handle callback '{}' for chat {}: {}", callbackData, chatId, e.getMessage());
            answerCallback(callbackQueryId, "Something went wrong. Please try /start again.", true);
        }
    }

    // ─────────────────────────────── step 1-2: name / age ───────────────────────────────

    private void startNameStep(long chatId, Integer messageId, ConversationState state) {
        state.setStep(ConversationStep.NAME);
        stateStore.save(state);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("Great! First, what's your name?")
                .build());
    }

    private void handleNameInput(long chatId, ConversationState state, String text) {
        if (!ProfileInputValidator.isValidName(text)) {
            send(SendMessage.builder().chatId(chatId)
                    .text("Please send a name up to 50 characters.")
                    .build());
            return;
        }
        state.setName(text.trim());
        advanceAfter(chatId, state, ConversationStep.NAME);
    }

    private void handleAgeInput(long chatId, ConversationState state, String text) {
        OptionalInt age = ProfileInputValidator.parseValidAge(text);
        if (age.isEmpty()) {
            send(SendMessage.builder().chatId(chatId)
                    .text("Please enter a number between 18 and 99.")
                    .build());
            return;
        }
        state.setAge(age.getAsInt());
        advanceAfter(chatId, state, ConversationStep.AGE);
    }

    private void promptAge(long chatId) {
        send(SendMessage.builder().chatId(chatId).text("How old are you? (18-99)").build());
    }

    // ─────────────────────────────── step 3: sex and orientation ───────────────────────────────

    private void promptGender(long chatId) {
        sendNew(chatId, "What is your sex?", genderKeyboard());
    }

    private InlineKeyboardMarkup genderKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        button("Male", "gender:MALE"),
                        button("Female", "gender:FEMALE"),
                        button("Non-binary", "gender:NON_BINARY")))
                .keyboardRow(List.of(button("⬅️ Back", "back")))
                .build();
    }

    private void promptOrientation(long chatId) {
        sendNew(chatId, "What is your orientation?", orientationKeyboard());
    }

    private InlineKeyboardMarkup orientationKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("Straight", "orientation:STRAIGHT"), button("Gay", "orientation:GAY")))
                .keyboardRow(List.of(button("Bisexual", "orientation:BISEXUAL"), button("Other", "orientation:OTHER")))
                .keyboardRow(List.of(button("⬅️ Back", "back")))
                .build();
    }

    // ─────────────────────────────── step 4: seeking genders (multi-select) ───────────────────────────────

    private void promptSeekingGenders(long chatId, ConversationState state) {
        sendNew(chatId, "Who are you interested in meeting? (select all that apply)", seekingGendersKeyboard(state));
    }

    private InlineKeyboardMarkup seekingGendersKeyboard(ConversationState state) {
        List<InlineKeyboardButton> options = GENDER_OPTIONS.stream()
                .map(gender -> button(
                        (state.seekingGenders().contains(gender) ? "✅ " : "") + humanize(gender),
                        "seek:" + gender))
                .toList();
        return InlineKeyboardMarkup.builder()
                .keyboardRow(options)
                .keyboardRow(List.of(button("➡️ Continue", "seek:continue")))
                .keyboardRow(List.of(button("⬅️ Back", "back")))
                .build();
    }

    private void handleDynamicOrGenderOrSeekingCallback(
            long chatId, Integer messageId, String callbackQueryId, ConversationState state, String callbackData) {
        if (callbackData.startsWith("orientation:")) {
            ackSilently(callbackQueryId);
            handleOrientationChoice(chatId, messageId, state, callbackData.substring("orientation:".length()));
        } else if (callbackData.startsWith("gender:")) {
            ackSilently(callbackQueryId);
            handleGenderChoice(chatId, messageId, state, callbackData.substring("gender:".length()));
        } else if (callbackData.startsWith("scope:")) {
            ackSilently(callbackQueryId);
            handleScopeChoice(chatId, messageId, state, callbackData.substring("scope:".length()));
        } else if (callbackData.startsWith("setscope:")) {
            ackSilently(callbackQueryId);
            handleSettingsScopeChoice(chatId, messageId, state.telegramUserId(), callbackData.substring("setscope:".length()));
        } else if (callbackData.startsWith("seek:")) {
            handleSeekingGenderToggle(chatId, messageId, callbackQueryId, state, callbackData.substring("seek:".length()));
        } else if (callbackData.startsWith("photo_manage:remove:")) {
            ackSilently(callbackQueryId);
            int index = Integer.parseInt(callbackData.substring("photo_manage:remove:".length()));
            handlePhotoManageRemove(chatId, state, index);
        } else {
            log.warn("Unrecognized callback data: {}", callbackData);
            answerCallback(callbackQueryId, null, false);
        }
    }

    private void handleGenderChoice(long chatId, Integer messageId, ConversationState state, String gender) {
        state.setGender(gender);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("Gender: " + humanize(gender) + " ✅")
                .build());
        advanceAfter(chatId, state, ConversationStep.GENDER);
    }

    private void handleSeekingGenderToggle(
            long chatId, Integer messageId, String callbackQueryId, ConversationState state, String gender) {
        state.toggleSeekingGender(gender);
        stateStore.save(state);
        send(EditMessageReplyMarkup.builder().chatId(chatId).messageId(messageId)
                .replyMarkup(seekingGendersKeyboard(state))
                .build());
        ackSilently(callbackQueryId);
    }

    private void handleSeekingGendersContinue(long chatId, Integer messageId, String callbackQueryId, ConversationState state) {
        if (state.seekingGenders().isEmpty()) {
            answerCallback(callbackQueryId, "Please select at least one option first.", true);
            return;
        }
        ackSilently(callbackQueryId);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("Looking for: " + joinHumanized(state.seekingGenders()) + " ✅")
                .build());
        advanceAfter(chatId, state, ConversationStep.SEEKING_GENDERS);
    }

    private void handleOrientationChoice(long chatId, Integer messageId, ConversationState state, String value) {
        state.setOrientation(value);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("Orientation: " + humanize(value) + " ✅").build());
        state.setStep(ConversationStep.SEEKING_GENDERS);
        stateStore.save(state);
        promptSeekingGenders(chatId, state);
    }

    // ─────────────────────────────── step 5: location ───────────────────────────────

    private void promptLocationChoice(long chatId) {
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("📍 Share My Location", "loc:share")))
                .keyboardRow(List.of(button("✍️ Enter Manually", "loc:manual")))
                .keyboardRow(List.of(button("⬅️ Back", "back")))
                .build();
        sendNew(chatId, "How would you like to set your location?", keyboard);
    }

    private void handleLocationShareChoice(long chatId, Integer messageId, ConversationState state) {
        state.setStep(ConversationStep.LOCATION_SHARE_PENDING);
        stateStore.save(state);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("Tap the button below to share your location.")
                .build());

        KeyboardRow row = new KeyboardRow();
        row.add(KeyboardButton.builder().text("📍 Share My Location").requestLocation(true).build());
        ReplyKeyboardMarkup replyKeyboard = ReplyKeyboardMarkup.builder()
                .keyboardRow(row)
                .resizeKeyboard(true)
                .oneTimeKeyboard(true)
                .build();
        send(SendMessage.builder().chatId(chatId).text("Waiting for your location...").replyMarkup(replyKeyboard).build());
    }

    private void handleLocationManualChoice(long chatId, Integer messageId, ConversationState state) {
        state.setStep(ConversationStep.LOCATION_MANUAL_COUNTRY);
        stateStore.save(state);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("Let's enter your location manually.")
                .build());
        promptManualCountry(chatId);
    }

    private void promptManualCountry(long chatId) {
        send(SendMessage.builder().chatId(chatId).text("What country are you in?").build());
    }

    private void promptManualCity(long chatId) {
        send(SendMessage.builder().chatId(chatId).text("What city are you in?").build());
    }

    private void handleManualCountryInput(long chatId, ConversationState state, String text) {
        if (!ProfileInputValidator.isValidLocationText(text)) {
            send(SendMessage.builder().chatId(chatId).text("Please enter a valid country name.").build());
            return;
        }
        state.setPendingCountry(text.trim());
        state.setStep(ConversationStep.LOCATION_MANUAL_CITY);
        stateStore.save(state);
        promptManualCity(chatId);
    }

    private void handleManualCityInput(long chatId, ConversationState state, String text) {
        if (!ProfileInputValidator.isValidLocationText(text)) {
            send(SendMessage.builder().chatId(chatId).text("Please enter a valid city name.").build());
            return;
        }
        state.setPendingCity(text.trim());
        state.confirmPendingLocation();
        if (state.isReturnToReview()) {
            state.setReturnToReview(false);
            showReview(chatId, state);
            return;
        }
        advanceAfter(chatId, state, ConversationStep.LOCATION_MANUAL_CITY);
    }

    private void promptLocationConfirm(long chatId, LocationResult result) {
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("✅ Yes", "loc:confirm_yes"), button("✍️ No, enter manually", "loc:confirm_no")))
                .keyboardRow(List.of(button("⬅️ Back", "back")))
                .build();
        sendNew(chatId, "📍 Detected: " + result.city() + ", " + result.country() + " — is this correct?", keyboard);
    }

    private void handleLocationConfirmYes(long chatId, Integer messageId, ConversationState state) {
        state.confirmPendingLocation();
        if (state.city() == null || state.city().isBlank() || state.country() == null || state.country().isBlank()) {
            state.setStep(ConversationStep.LOCATION_MANUAL_COUNTRY);
            stateStore.save(state);
            send(SendMessage.builder().chatId(chatId)
                    .text("We couldn't identify both city and country. Please enter them manually.").build());
            promptManualCountry(chatId);
            return;
        }
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("📍 Location confirmed: " + state.city() + ", " + state.country())
                .build());
        advanceAfter(chatId, state, ConversationStep.LOCATION_CONFIRM);
    }

    private void handleLocationConfirmNo(long chatId, Integer messageId, ConversationState state) {
        state.setStep(ConversationStep.LOCATION_MANUAL_COUNTRY);
        stateStore.save(state);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("No problem, let's enter it manually.")
                .build());
        promptManualCountry(chatId);
    }

    // ─────────────────────────────── descriptions ───────────────────────────────

    private void promptSelfDescription(long chatId) {
        send(SendMessage.builder().chatId(chatId)
                .text("About me: tell us about your personality, lifestyle, interests, and what matters to you. "
                        + "You can mention things like your education, work, diet, smoking/drinking, pets, religion, "
                        + "children, languages, or anything else you want a potential partner to know. "
                        + "Only details you explicitly write may be used to fill optional profile fields; "
                        + "unstated details will be left blank. Please write at least 10 characters.")
                .build());
    }

    private void promptPreferenceDescription(long chatId) {
        send(SendMessage.builder().chatId(chatId)
                .text("About you: describe the person you are looking for, including any important "
                        + "preferences or deal-breakers. You can mention lifestyle or demographic "
                        + "preferences if relevant. We will not guess or infer anything you don't state. "
                        + "Please write at least 10 characters.")
                .build());
    }

    private void handleSelfDescriptionInput(long chatId, ConversationState state, String text) {
        if (!ProfileInputValidator.isValidDescription(text)) {
            send(SendMessage.builder().chatId(chatId)
                    .text("Please write a bit more (at least 10 characters).")
                    .build());
            return;
        }
        state.setSelfDescription(text.trim());
        advanceAfter(chatId, state, ConversationStep.SELF_DESCRIPTION);
    }

    private void handlePreferenceDescriptionInput(long chatId, ConversationState state, String text) {
        if (!ProfileInputValidator.isValidDescription(text)) {
            send(SendMessage.builder().chatId(chatId)
                    .text("Please write a bit more (at least 10 characters).")
                    .build());
            return;
        }
        state.setPreferenceDescription(text.trim());
        advanceAfter(chatId, state, ConversationStep.PREFERENCE_DESCRIPTION);
    }

    // ─────────────────────────────── step (new): photos ───────────────────────────────

    private void promptPhotos(long chatId, ConversationState state) {
        state.setStep(ConversationStep.PHOTOS);
        stateStore.save(state);
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("🔗 Add Photo URL", "photo:add")))
                .keyboardRow(List.of(button("⏭ Skip for now", "photo:skip")))
                .build();
        sendNew(chatId, "Would you like to add a photo URL? (optional, up to " + MAX_PHOTOS
                + "). The reference is stored only; it is never fetched or analyzed.", keyboard);
    }

    private void handlePhotoAddPrompt(long chatId, ConversationState state) {
        state.setStep(ConversationStep.PHOTOS);
        stateStore.save(state);
        send(SendMessage.builder().chatId(chatId)
                .text("Send a photo URL (https://...). I will store the reference only; I will not fetch or analyze it.")
                .build());
    }

    private void handlePhotoUrlInput(long chatId, ConversationState state, String url) {
        if (!ProfileInputValidator.isValidPhotoUrl(url)) {
            send(SendMessage.builder().chatId(chatId)
                    .text("Please send a valid http:// or https:// photo URL, or choose Done/Skip.")
                    .build());
            return;
        }
        boolean added = state.addPhotoUrn(url.trim());
        stateStore.save(state);
        int count = state.photoUrns().size();
        if (!added) {
            send(SendMessage.builder().chatId(chatId)
                    .text("You've reached the " + MAX_PHOTOS + "-URL limit.")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                            .keyboardRow(List.of(button("✅ Done Adding Photos", "photo:done")))
                            .build())
                    .build());
            return;
        }

        List<InlineKeyboardButton> buttons = new ArrayList<>();
        if (count < MAX_PHOTOS) {
            buttons.add(button("➕ Add Another", "photo:add_another"));
        }
        buttons.add(button("✅ Done Adding Photos", "photo:done"));
        send(SendMessage.builder().chatId(chatId)
                .text("✅ Photo URL added (" + count + "/" + MAX_PHOTOS + ").")
                .replyMarkup(InlineKeyboardMarkup.builder().keyboardRow(buttons).build())
                .build());
    }

    private void handlePhotosDone(long chatId, ConversationState state) {
        advanceAfter(chatId, state, ConversationStep.PHOTOS);
    }

    // ─────────────────────────────── photo management (Review "Edit Photos") ───────────────────────────────

    private void jumpToEditPhotos(long chatId, Integer messageId, ConversationState state) {
        state.setStep(ConversationStep.PHOTO_MANAGE);
        stateStore.save(state);
        removeKeyboard(chatId, messageId);
        renderPhotoManageView(chatId, state);
    }

    private void renderPhotoManageView(long chatId, ConversationState state) {
        List<String> photos = state.photoUrns();

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < photos.size(); i++) {
            rows.add(List.of(button("🗑 Remove URL " + (i + 1), "photo_manage:remove:" + i)));
        }
        List<InlineKeyboardButton> lastRow = new ArrayList<>();
        if (photos.size() < MAX_PHOTOS) {
            lastRow.add(button("➕ Add URL", "photo_manage:add"));
        }
        lastRow.add(button("✅ Done", "photo_manage:done"));
        rows.add(lastRow);

        InlineKeyboardMarkup.InlineKeyboardMarkupBuilder keyboardBuilder = InlineKeyboardMarkup.builder();
        rows.forEach(keyboardBuilder::keyboardRow);
        String text = photos.isEmpty() ? "You haven't added any photo URLs yet."
                : "Manage photo URL references (stored only; not fetched or analyzed):\n" + numbered(photos);
        sendNew(chatId, text, keyboardBuilder.build());
    }

    private void handlePhotoManageAdd(long chatId, ConversationState state) {
        state.setReturnToReview(true);
        state.setStep(ConversationStep.PHOTOS);
        stateStore.save(state);
        send(SendMessage.builder().chatId(chatId).text("Send a photo URL (https://...).").build());
    }

    private void handlePhotoManageRemove(long chatId, ConversationState state, int index) {
        state.removePhotoUrnAt(index);
        stateStore.save(state);
        renderPhotoManageView(chatId, state);
    }

    // ─────────────────────────────── step 5b: default search scope ───────────────────────────────

    private InlineKeyboardMarkup scopeKeyboard(String prefix) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("🏙 My city", prefix + "CITY")))
                .keyboardRow(List.of(button("🌍 My country", prefix + "COUNTRY")))
                .keyboardRow(List.of(button("🌐 Worldwide", prefix + "WORLDWIDE")))
                .build();
    }

    private void promptSearchScope(long chatId) {
        sendNew(chatId, "Where should I look for matches? You can change this later in Settings.",
                scopeKeyboard("scope:"));
    }

    private void handleScopeChoice(long chatId, Integer messageId, ConversationState state, String scope) {
        if (!isValidScope(scope)) {
            return;
        }
        state.setSearchScope(scope);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("Search scope: " + humanizeScope(scope) + " ✅").build());
        advanceAfter(chatId, state, ConversationStep.SEARCH_SCOPE);
    }

    private void jumpToEditScope(long chatId, Integer messageId, ConversationState state) {
        state.setReturnToReview(true);
        state.setStep(ConversationStep.SEARCH_SCOPE);
        stateStore.save(state);
        removeKeyboard(chatId, messageId);
        promptSearchScope(chatId);
    }

    private void showSettingsScopeMenu(long chatId) {
        sendNew(chatId, "🌍 Choose your default search scope:", scopeKeyboard("setscope:"));
    }

    private void handleSettingsScopeChoice(long chatId, Integer messageId, String telegramUserId, String scope) {
        if (!isValidScope(scope)) {
            return;
        }
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            backendApiClient.updateSearchScope(String.valueOf(profile.get("id")), scope);
            send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                    .text("Search scope updated: " + humanizeScope(scope) + " ✅").build());
        } catch (Exception e) {
            log.warn("Failed to update search scope for chat {}: {}", chatId, e.getMessage());
            send(SendMessage.builder().chatId(chatId).text("Couldn't update your search scope. Try again shortly.").build());
        }
    }

    private static boolean isValidScope(String scope) {
        return "CITY".equals(scope) || "COUNTRY".equals(scope) || "WORLDWIDE".equals(scope);
    }

    private static String humanizeScope(String scope) {
        return switch (scope) {
            case "CITY" -> "My city";
            case "COUNTRY" -> "My country";
            default -> "Worldwide";
        };
    }

    // ─────────────────────────────── step 5c: preferred age range ───────────────────────────────

    private static int[] defaultAgeRange(Integer age) {
        int base = age == null ? 25 : age;
        return new int[] {Math.max(18, base - 3), Math.min(99, base + 3)};
    }

    private InlineKeyboardMarkup ageRangeKeyboard(Integer age) {
        int[] range = defaultAgeRange(age);
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("Use my age \u00b13 (" + range[0] + "-" + range[1] + ")", "agerange:default")))
                .build();
    }

    private void promptAgeRange(long chatId, ConversationState state) {
        sendNew(chatId, "What age range should your matches be in? Send it like 25-35, or use the default below.",
                ageRangeKeyboard(state.age()));
    }

    private void handleAgeRangeInput(long chatId, ConversationState state, String text) {
        var range = ProfileInputValidator.parseAgeRange(text);
        if (range.isEmpty()) {
            send(SendMessage.builder().chatId(chatId)
                    .text("Please send a range like 25-35 (ages 18-99, minimum not above maximum).").build());
            return;
        }
        state.setPreferredAgeRange(range.get()[0], range.get()[1]);
        send(SendMessage.builder().chatId(chatId)
                .text("Preferred age range: " + range.get()[0] + "-" + range.get()[1] + " \u2705").build());
        advanceAfter(chatId, state, ConversationStep.AGE_RANGE);
    }

    private void handleAgeRangeDefault(long chatId, Integer messageId, ConversationState state) {
        int[] range = defaultAgeRange(state.age());
        state.setPreferredAgeRange(range[0], range[1]);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("Preferred age range: " + range[0] + "-" + range[1] + " \u2705").build());
        advanceAfter(chatId, state, ConversationStep.AGE_RANGE);
    }

    private void jumpToEditAgeRange(long chatId, Integer messageId, ConversationState state) {
        state.setReturnToReview(true);
        state.setStep(ConversationStep.AGE_RANGE);
        stateStore.save(state);
        removeKeyboard(chatId, messageId);
        promptAgeRange(chatId, state);
    }

    private void promptSettingsAgeRange(long chatId, ConversationState state) {
        state.setStep(ConversationStep.SETTINGS_AGE_RANGE);
        stateStore.save(state);
        send(SendMessage.builder().chatId(chatId)
                .text("\ud83c\udfaf Send your new preferred match age range, like 25-35.").build());
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
            state.setStep(ConversationStep.SETTINGS_MENU);
            stateStore.save(state);
            send(SendMessage.builder().chatId(chatId)
                    .text("Preferred age range updated: " + range.get()[0] + "-" + range.get()[1] + " \u2705").build());
        } catch (Exception e) {
            log.warn("Failed to update age range for chat {}: {}", chatId, e.getMessage());
            send(SendMessage.builder().chatId(chatId).text("Couldn't update your age range. Try again shortly.").build());
        }
    }

    // ─────────────────────────────── step 8: review & confirm ───────────────────────────────

    private void showReview(long chatId, ConversationState state) {
        state.setStep(ConversationStep.REVIEW);
        stateStore.save(state);
        sendNew(chatId, formatReview(state), reviewKeyboard());
    }

    private String formatReview(ConversationState state) {
        String photoLine = state.photoUrns().isEmpty()
                ? "📷 Photo URLs: none added"
                : "📷 Photo URLs: " + state.photoUrns().size() + "/" + MAX_PHOTOS + " added";
        return """
                Here's your profile — take a look:

                ━━━━━━━━━━━━━━━
                👤 %s, %d, %s (%s)
                📍 %s, %s
                🌍 Search scope: %s
                🎯 Match ages: %s
                🔍 Looking for: %s
                %s

                "%s"

                Looking for: "%s"
                ━━━━━━━━━━━━━━━""".formatted(
                state.name(), state.age(), humanize(state.gender()), humanize(state.orientation()),
                state.city(), state.country(),
                humanizeScope(state.searchScope()),
                state.minPreferredAge() == null ? "not set" : state.minPreferredAge() + "-" + state.maxPreferredAge(),
                joinHumanized(state.seekingGenders()),
                photoLine,
                state.selfDescription(),
                state.preferenceDescription());
    }

    private InlineKeyboardMarkup reviewKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("✅ Looks good, save it!", "review:save")))
                .keyboardRow(List.of(button("✏️ Edit Name", "review:edit_name"), button("✏️ Edit Age", "review:edit_age")))
                .keyboardRow(List.of(button("✏️ Edit Sex", "review:edit_gender"), button("✏️ Edit Orientation", "review:edit_orientation")))
                .keyboardRow(List.of(button("✏️ Edit Location", "review:edit_location"), button("✏️ Edit Descriptions", "review:edit_desc")))
                .keyboardRow(List.of(button("✏️ Edit Photos", "review:edit_photos"), button("✏️ Edit Search Scope", "review:edit_scope")))
                .keyboardRow(List.of(button("✏️ Edit Age Range", "review:edit_agerange")))
                .keyboardRow(List.of(button("⬅️ Back", "back")))
                .build();
    }

    private void jumpToEdit(long chatId, Integer messageId, ConversationState state, ConversationStep target) {
        state.setReturnToReview(true);
        state.setStep(target);
        stateStore.save(state);
        removeKeyboard(chatId, messageId);
        switch (target) {
            case NAME -> send(SendMessage.builder().chatId(chatId).text("What's your name?").build());
            case AGE -> promptAge(chatId);
            case SELF_DESCRIPTION -> promptSelfDescription(chatId);
            default -> throw new IllegalStateException("Unsupported direct edit target: " + target);
        }
    }

    private void jumpToEditGender(long chatId, Integer messageId, ConversationState state) {
        state.setReturnToReview(true);
        state.setStep(ConversationStep.GENDER);
        stateStore.save(state);
        removeKeyboard(chatId, messageId);
        promptGender(chatId);
    }

    private void jumpToEditOrientation(long chatId, Integer messageId, ConversationState state) {
        state.setReturnToReview(true);
        state.setStep(ConversationStep.ORIENTATION);
        stateStore.save(state);
        removeKeyboard(chatId, messageId);
        promptOrientation(chatId);
    }

    private void jumpToEditLocation(long chatId, Integer messageId, ConversationState state) {
        state.setReturnToReview(true);
        state.setStep(ConversationStep.LOCATION_CHOICE);
        stateStore.save(state);
        removeKeyboard(chatId, messageId);
        promptLocationChoice(chatId);
    }

    private void handleReviewSave(long chatId, Integer messageId, String telegramUserId, ConversationState state) {
        Map<String, Object> saved = backendApiClient.createProfile(
                telegramUserId,
                state.name(),
                state.age(),
                state.gender(),
                state.orientation(),
                state.seekingGenders(),
                state.country(),
                state.city(),
                state.selfDescription(),
                state.preferenceDescription(),
                state.photoUrns(),
                state.searchScope(),
                state.minPreferredAge(),
                state.maxPreferredAge());
        String profileId = String.valueOf(saved.get("id"));
        backendApiClient.generateEmbeddings(profileId);
        stateStore.clear(telegramUserId);

        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("🎉 Your profile is live!")
                .build());
        sendMainMenu(chatId, telegramUserId);
    }

    private void handleShowMatches(long chatId, String telegramUserId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            String profileId = String.valueOf(profile.get("id"));
            List<Map<String, Object>> recommendations =
                    backendApiClient.getRecommendations(profileId, DEFAULT_TOP_N);
            send(SendMessage.builder().chatId(chatId).text(formatRecommendations(recommendations)).build());
        } catch (Exception e) {
            log.warn("Failed to fetch matches for chat {}: {}", chatId, e.getMessage());
            send(SendMessage.builder().chatId(chatId).text("Couldn't fetch matches right now. Try again shortly.").build());
        }
    }

    private void handleAdjustPreferences(long chatId) {
        send(SendMessage.builder().chatId(chatId)
                .text("Send a message describing how you'd like to adjust your preferences (e.g. \"I want someone calmer\").")
                .build());
    }

    private void handlePostOnboardingFreeText(long chatId, String telegramUserId, String text) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            String profileId = String.valueOf(profile.get("id"));
            Map<String, Object> result = backendApiClient.refinePreference(profileId, text, DEFAULT_TOP_N);
            StringBuilder response = new StringBuilder();
            response.append("Updated: ").append(result.getOrDefault("changeSummary", "")).append("\n\n");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recommendations =
                    (List<Map<String, Object>>) result.getOrDefault("recommendations", List.of());
            response.append(formatRecommendations(recommendations));
            send(SendMessage.builder().chatId(chatId).text(response.toString()).build());
        } catch (Exception e) {
            log.warn("Failed to handle free-text refinement for chat {}: {}", chatId, e.getMessage());
            send(SendMessage.builder().chatId(chatId).text("Something went wrong. Please try again later.").build());
        }
    }

    private String formatRecommendations(List<Map<String, Object>> recommendations) {
        if (recommendations.isEmpty()) {
            return "No matches available right now.";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> r : recommendations) {
            sb.append("• %s (score: %.2f)\n".formatted(r.get("displayName"), ((Number) r.get("aggregatedScore")).doubleValue()));
        }
        return sb.toString().stripTrailing();
    }

    private String numbered(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            result.append(i + 1).append(". ").append(values.get(i));
            if (i + 1 < values.size()) result.append('\n');
        }
        return result.toString();
    }

    // ─────────────────────────────── main menu ───────────────────────────────

    /**
     * Single reusable entry point for the persistent main menu — called after profile creation,
     * after {@code /menu} or {@code /start} (for a user who already has a profile), and from every
     * "⬅️ Back to Menu" button, so the menu-building logic lives in exactly one place.
     */
    private void sendMainMenu(long chatId, String telegramUserId) {
        browsingQueues.remove(telegramUserId);
        browsingModes.remove(telegramUserId);
        currentCandidates.remove(telegramUserId);

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("👤 My Profile", "menu:profile")))
                .keyboardRow(List.of(button("💘 My Matches", "menu:matches")))
                .keyboardRow(List.of(button("❤️ Who Liked Me", "menu:liked")))
                .keyboardRow(List.of(button("⚙️ Settings", "menu:settings")))
                .build();
        sendNew(chatId, "What would you like to do?", keyboard);
    }

    private InlineKeyboardMarkup backToMenuKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("⬅️ Back to Menu", "menu:back")))
                .build();
    }

    // ─────────────────────────────── my profile ───────────────────────────────

    private void handleMyProfile(long chatId, String telegramUserId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            String card = formatProfileCard(profile, true);
            InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                    .keyboardRow(List.of(button("✏️ Edit My Profile", "profile:edit")))
                    .keyboardRow(List.of(button("⬅️ Back to Menu", "menu:back")))
                    .build();
            sendCandidateCard(chatId, profile, card, keyboard);
        } catch (Exception e) {
            log.warn("Failed to load own profile for chat {}: {}", chatId, e.getMessage());
            send(SendMessage.builder().chatId(chatId).text("We couldn't load your profile. Send /start to create one.").build());
        }
    }

    // ─────────────────────────────── my matches / who liked me (browsing) ───────────────────────────────

    private void handleMyMatches(long chatId, String telegramUserId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            String profileId = String.valueOf(profile.get("id"));
            List<Map<String, Object>> recommendations =
                    backendApiClient.getRecommendations(profileId, BROWSE_FETCH_SIZE);
            recommendations.forEach(candidate -> candidate.putIfAbsent("id", candidate.get("candidateId")));
            browsingQueues.put(telegramUserId, new ArrayDeque<>(recommendations));
            browsingModes.put(telegramUserId, "MATCHES");
            showNextBrowsingCard(chatId, telegramUserId);
        } catch (Exception e) {
            log.warn("Failed to fetch matches for chat {}: {}", chatId, e.getMessage());
            send(SendMessage.builder().chatId(chatId).text("Couldn't fetch matches right now. Try again shortly.").build());
        }
    }

    private void handleWhoLikedMe(long chatId, String telegramUserId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            String profileId = String.valueOf(profile.get("id"));
            List<Map<String, Object>> likers = backendApiClient.getProfilesWhoLikedMe(profileId);
            if (likers.isEmpty()) {
                send(SendMessage.builder().chatId(chatId)
                        .text("No one yet — but check back soon!")
                        .replyMarkup(backToMenuKeyboard())
                        .build());
                return;
            }
            browsingQueues.put(telegramUserId, new ArrayDeque<>(likers));
            browsingModes.put(telegramUserId, "LIKED_ME");
            showNextBrowsingCard(chatId, telegramUserId);
        } catch (Exception e) {
            log.warn("Failed to fetch who-liked-me for chat {}: {}", chatId, e.getMessage());
            send(SendMessage.builder().chatId(chatId).text("Couldn't load this right now. Try again shortly.").build());
        }
    }

    private void showNextBrowsingCard(long chatId, String telegramUserId) {
        Deque<Map<String, Object>> queue = browsingQueues.get(telegramUserId);
        if (queue == null || queue.isEmpty()) {
            browsingQueues.remove(telegramUserId);
            browsingModes.remove(telegramUserId);
            currentCandidates.remove(telegramUserId);
            send(SendMessage.builder().chatId(chatId)
                    .text("That's everyone for now — check back later!")
                    .replyMarkup(backToMenuKeyboard())
                    .build());
            return;
        }

        Map<String, Object> candidate = queue.poll();
        currentCandidates.put(telegramUserId, candidate);
        String mode = browsingModes.getOrDefault(telegramUserId, "MATCHES");
        String card = formatProfileCard(candidate, false);

        InlineKeyboardMarkup keyboard = "LIKED_ME".equals(mode)
                ? InlineKeyboardMarkup.builder()
                        .keyboardRow(List.of(button("👍 Like Back", "browse:likeback")))
                        .keyboardRow(List.of(button("➡️ Next", "browse:next")))
                        .keyboardRow(List.of(button("⬅️ Back to Menu", "menu:back")))
                        .build()
                : InlineKeyboardMarkup.builder()
                        .keyboardRow(List.of(button("👍 Like", "browse:like"), button("👎 Skip", "browse:skip")))
                        .keyboardRow(List.of(button("⬅️ Back to Menu", "menu:back")))
                        .build();

        sendCandidateCard(chatId, candidate, card, keyboard);
    }

    private void handleBrowseLike(long chatId, String telegramUserId) {
        Map<String, Object> candidate = currentCandidates.get(telegramUserId);
        if (candidate == null) {
            showNextBrowsingCard(chatId, telegramUserId);
            return;
        }
        try {
            Map<String, Object> ownProfile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            String ownProfileId = String.valueOf(ownProfile.get("id"));
            String candidateId = String.valueOf(candidate.get("id"));
            backendApiClient.recordLike(ownProfileId, candidateId);
        } catch (Exception e) {
            log.warn("Failed to record like for chat {}: {}", chatId, e.getMessage());
        }
        showNextBrowsingCard(chatId, telegramUserId);
    }

    // ─────────────────────────────── shared profile-card rendering ───────────────────────────────

    @SuppressWarnings("unchecked")
    private String formatProfileCard(Map<String, Object> profile, boolean includePreference) {
        Object age = profile.get("age");
        StringBuilder sb = new StringBuilder();
        sb.append("👤 %s, %s\n".formatted(profile.get("displayName"), age));
        String city = (String) profile.get("city");
        String country = (String) profile.get("country");
        if (city != null || country != null) {
            sb.append("📍 %s, %s\n".formatted(city, country));
        }
        sb.append("\n\"%s\"".formatted(profile.get("selfDescription")));
        if (includePreference && profile.get("preferenceDescription") != null) {
            sb.append("\n\nLooking for: \"%s\"".formatted(profile.get("preferenceDescription")));
        }
        return sb.toString();
    }

    private void sendCandidateCard(long chatId, Map<String, Object> profile, String caption, InlineKeyboardMarkup keyboard) {
        Object photoUrns = profile.get("photoUrns");
        if (photoUrns instanceof List<?> urns && !urns.isEmpty()) {
            caption += "\n📷 Photo references: " + urns.size();
        }
        send(SendMessage.builder().chatId(chatId).text(caption).replyMarkup(keyboard).build());
    }

    // ─────────────────────────────── settings / deletion ───────────────────────────────

    private InlineKeyboardMarkup settingsKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("✏️ Edit My Profile", "settings:edit")))
                .keyboardRow(List.of(button("🌍 Search Scope", "settings:scope")))
                .keyboardRow(List.of(button("🎯 Match Age Range", "settings:agerange")))
                .keyboardRow(List.of(button("🔕 Pause Matching", "settings:pause")))
                .keyboardRow(List.of(button("🗑 Delete My Account", "settings:delete")))
                .build();
    }

    private void handleSettingsEditProfile(long chatId, String telegramUserId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            ConversationState state = fromExistingProfile(telegramUserId, profile);
            stateStore.save(state);
            showReview(chatId, state);
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
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("❌ Yes, delete everything", "delete:yes")))
                .keyboardRow(List.of(button("⬅️ No, keep my profile", "delete:no")))
                .build();
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("⚠️ Are you sure? This cannot be undone.")
                .replyMarkup(keyboard)
                .build());
    }

    private void handleDeleteConfirmed(long chatId, Integer messageId, String telegramUserId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            backendApiClient.deleteProfile(String.valueOf(profile.get("id")));
        } catch (Exception e) {
            log.warn("Failed to delete profile for chat {}: {}", chatId, e.getMessage());
        }
        stateStore.clear(telegramUserId);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("Your account has been deleted. Send /start any time to create a new profile.")
                .build());
    }

    private void handleDeleteCancelled(long chatId, Integer messageId, ConversationState state) {
        state.setStep(ConversationStep.SETTINGS_MENU);
        stateStore.save(state);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("⚙️ Settings")
                .replyMarkup(settingsKeyboard())
                .build());
    }

    // ─────────────────────────────── back navigation ───────────────────────────────

    private void handleBack(long chatId, Integer messageId, ConversationState state) {
        switch (state.step()) {
            case GENDER -> {
                state.setStep(ConversationStep.AGE);
                stateStore.save(state);
                removeKeyboard(chatId, messageId);
                promptAge(chatId);
            }
            case ORIENTATION -> {
                state.setStep(ConversationStep.GENDER);
                stateStore.save(state);
                send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                        .text("What is your sex?").replyMarkup(genderKeyboard()).build());
            }
            case SEEKING_GENDERS -> {
                state.setStep(ConversationStep.ORIENTATION);
                stateStore.save(state);
                send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                        .text("What is your orientation?")
                        .replyMarkup(orientationKeyboard())
                        .build());
            }
            case LOCATION_CHOICE -> {
                state.setStep(ConversationStep.SEEKING_GENDERS);
                stateStore.save(state);
                send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                        .text("Who are you interested in meeting? (select all that apply)")
                        .replyMarkup(seekingGendersKeyboard(state))
                        .build());
            }
            case LOCATION_CONFIRM -> {
                state.setStep(ConversationStep.LOCATION_CHOICE);
                stateStore.save(state);
                InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                        .keyboardRow(List.of(button("📍 Share My Location", "loc:share")))
                        .keyboardRow(List.of(button("✍️ Enter Manually", "loc:manual")))
                        .keyboardRow(List.of(button("⬅️ Back", "back")))
                        .build();
                send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                        .text("How would you like to set your location?")
                        .replyMarkup(keyboard)
                        .build());
            }
            case REVIEW -> {
                state.setStep(ConversationStep.PREFERENCE_DESCRIPTION);
                stateStore.save(state);
                removeKeyboard(chatId, messageId);
                promptPreferenceDescription(chatId);
            }
            default -> log.warn("Back pressed on a step with no defined back-target: {}", state.step());
        }
    }

    // ─────────────────────────────── linear step advancement ───────────────────────────────

    /**
     * Advances the conversation past {@code justCompleted}: normally to the next step in the
     * linear flow, but if {@code state.isReturnToReview()} is set (this field was reached via a
     * Review "Edit ..." button) and {@code justCompleted} is the LAST step of that field's edit
     * group, jumps straight back to {@link ConversationStep#REVIEW} instead — leaving every other
     * already-collected field untouched.
     */
    private void advanceAfter(long chatId, ConversationState state, ConversationStep justCompleted) {
        boolean isGroupTerminal = switch (justCompleted) {
            case NAME, AGE, SEEKING_GENDERS, LOCATION_MANUAL_CITY, LOCATION_CONFIRM, SEARCH_SCOPE, AGE_RANGE,
                    PREFERENCE_DESCRIPTION -> true;
            default -> false;
        };
        if (state.isReturnToReview() && isGroupTerminal) {
            state.setReturnToReview(false);
            showReview(chatId, state);
            return;
        }

        switch (justCompleted) {
            case NAME -> { state.setStep(ConversationStep.AGE); stateStore.save(state); promptAge(chatId); }
            case AGE -> { state.setStep(ConversationStep.GENDER); stateStore.save(state); promptGender(chatId); }
            case GENDER -> { state.setStep(ConversationStep.ORIENTATION); stateStore.save(state); promptOrientation(chatId); }
            case ORIENTATION -> { state.setStep(ConversationStep.SEEKING_GENDERS); stateStore.save(state); promptSeekingGenders(chatId, state); }
            case SEEKING_GENDERS -> { state.setStep(ConversationStep.LOCATION_CHOICE); stateStore.save(state); promptLocationChoice(chatId); }
            case LOCATION_MANUAL_CITY, LOCATION_CONFIRM -> {
                state.setStep(ConversationStep.SEARCH_SCOPE);
                stateStore.save(state);
                promptSearchScope(chatId);
            }
            case SEARCH_SCOPE -> {
                state.setStep(ConversationStep.AGE_RANGE);
                stateStore.save(state);
                promptAgeRange(chatId, state);
            }
            case AGE_RANGE -> {
                state.setStep(ConversationStep.SELF_DESCRIPTION);
                stateStore.save(state);
                promptSelfDescription(chatId);
            }
            case SELF_DESCRIPTION -> { state.setStep(ConversationStep.PREFERENCE_DESCRIPTION); stateStore.save(state); promptPreferenceDescription(chatId); }
            case PREFERENCE_DESCRIPTION -> promptPhotos(chatId, state);
            case PHOTOS -> {
                // Destination is REVIEW either way (normal linear completion or returning from an
                // edit-photos sub-loop), so no special-casing is needed here beyond resetting the flag.
                state.setReturnToReview(false);
                showReview(chatId, state);
            }
            default -> log.warn("advanceAfter called for a step with no defined successor: {}", justCompleted);
        }
    }

    // ─────────────────────────────── small Telegram helpers ───────────────────────────────

    private InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    private void sendNew(long chatId, String text, InlineKeyboardMarkup keyboard) {
        send(SendMessage.builder().chatId(chatId).text(text).replyMarkup(keyboard).build());
    }

    private void removeKeyboard(long chatId, Integer messageId) {
        send(EditMessageReplyMarkup.builder().chatId(chatId).messageId(messageId)
                .replyMarkup(InlineKeyboardMarkup.builder().build())
                .build());
    }

    private void ackSilently(String callbackQueryId) {
        answerCallback(callbackQueryId, null, false);
    }

    private void answerCallback(String callbackQueryId, String alertText, boolean showAlert) {
        AnswerCallbackQuery.AnswerCallbackQueryBuilder builder = AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId);
        if (alertText != null) {
            builder.text(alertText).showAlert(showAlert);
        }
        send(builder.build());
    }

    private <T extends Serializable, M extends BotApiMethod<T>> void send(M method) {
        try {
            sender.execute(method);
        } catch (TelegramApiException e) {
            log.warn("Failed to execute Telegram method {}: {}", method.getClass().getSimpleName(), e.getMessage());
        }
    }

    private static String humanize(String genderEnumName) {
        if (genderEnumName == null) {
            return "";
        }
        return switch (genderEnumName) {
            case "MALE" -> "Male";
            case "FEMALE" -> "Female";
            case "NON_BINARY" -> "Non-binary";
            default -> genderEnumName;
        };
    }

    private static String joinHumanized(java.util.Set<String> genders) {
        return genders.stream().map(ConversationFlowHandler::humanize).reduce((a, b) -> a + ", " + b).orElse("anyone");
    }
}
