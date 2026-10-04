package ua.kpi.project.compatme.adapter.telegram.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import ua.kpi.project.compatme.adapter.telegram.BackendApiClient;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationState;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStateStore;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStep;
import ua.kpi.project.compatme.adapter.telegram.ui.Keyboards;
import ua.kpi.project.compatme.adapter.telegram.ui.Labels;
import ua.kpi.project.compatme.adapter.telegram.ui.TelegramSender;
import ua.kpi.project.compatme.adapter.telegram.validation.ProfileInputValidator;
import ua.kpi.project.compatme.application.exception.ReverseGeocodingException;
import ua.kpi.project.compatme.application.port.out.ReverseGeocodingPort;
import ua.kpi.project.compatme.domain.model.LocationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Button-driven onboarding / profile-creation conversation (name through review and save), plus
 * the "Edit ..." jumps that return to review. Pure Telegram presentation: it only collects input
 * and delegates persistence to the backend REST API via {@link BackendApiClient}.
 */
public class OnboardingFlow {

    private static final Logger log = LoggerFactory.getLogger(OnboardingFlow.class);

    private static final List<String> GENDER_OPTIONS = List.of("MALE", "FEMALE", "NON_BINARY");
    private static final int MAX_PHOTOS = 5;

    private final TelegramSender telegram;
    private final BackendApiClient backendApiClient;
    private final ReverseGeocodingPort reverseGeocodingPort;
    private final ConversationStateStore stateStore;
    private final MenuFlow menuFlow;

    public OnboardingFlow(
            TelegramSender telegram,
            BackendApiClient backendApiClient,
            ReverseGeocodingPort reverseGeocodingPort,
            ConversationStateStore stateStore,
            MenuFlow menuFlow) {
        this.telegram = telegram;
        this.backendApiClient = backendApiClient;
        this.reverseGeocodingPort = reverseGeocodingPort;
        this.stateStore = stateStore;
        this.menuFlow = menuFlow;
    }

    // ─────────────────────────────── entry points ───────────────────────────────

    public void sendWelcome(long chatId, String telegramUserId) {
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

    /** Handles free text for onboarding steps; returns {@code false} if the step is not an onboarding text step. */
    public boolean onText(long chatId, ConversationState state, String text) {
        switch (state.step()) {
            case NAME -> handleNameInput(chatId, state, text);
            case AGE -> handleAgeInput(chatId, state, text);
            case LOCATION_MANUAL_COUNTRY -> handleManualCountryInput(chatId, state, text);
            case LOCATION_MANUAL_CITY -> handleManualCityInput(chatId, state, text);
            case AGE_RANGE -> handleAgeRangeInput(chatId, state, text);
            case SELF_DESCRIPTION -> handleSelfDescriptionInput(chatId, state, text);
            case PREFERENCE_DESCRIPTION -> handlePreferenceDescriptionInput(chatId, state, text);
            case PHOTOS -> handlePhotoUrlInput(chatId, state, text);
            default -> { return false; }
        }
        return true;
    }

    public void onLocationMessage(long chatId, String telegramUserId, double latitude, double longitude) {
        ConversationState state = stateStore.loadOrCreate(telegramUserId);
        if (state.step() != ConversationStep.LOCATION_SHARE_PENDING) {
            return;
        }
        send(SendMessage.builder().chatId(chatId).text("Got it, looking that up...")
                .replyMarkup(menuFlow.hasExistingProfile(telegramUserId)
                        ? ua.kpi.project.compatme.adapter.telegram.ui.MainMenuKeyboard.build()
                        : ReplyKeyboardRemove.builder().removeKeyboard(true).build())
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

    /** @return {@code true} if {@code data} belonged to the onboarding flow. */
    public boolean onCallback(long chatId, String telegramUserId, Integer messageId, String callbackQueryId,
                              String data, ConversationState state) {
        switch (data) {
            case "start_create" -> { ackSilently(callbackQueryId); startNameStep(chatId, messageId, state); }
            case "back" -> { ackSilently(callbackQueryId); handleBack(chatId, messageId, state); }
            case "loc:share" -> { ackSilently(callbackQueryId); handleLocationShareChoice(chatId, messageId, state); }
            case "loc:manual" -> { ackSilently(callbackQueryId); handleLocationManualChoice(chatId, messageId, state); }
            case "loc:confirm_yes" -> { ackSilently(callbackQueryId); handleLocationConfirmYes(chatId, messageId, state); }
            case "loc:confirm_no" -> { ackSilently(callbackQueryId); handleLocationConfirmNo(chatId, messageId, state); }
            case "review:edit_agerange" -> { ackSilently(callbackQueryId); jumpToEditAgeRange(chatId, messageId, state); }
            case "agerange:default" -> { ackSilently(callbackQueryId); handleAgeRangeDefault(chatId, messageId, state); }
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
            default -> { return onPrefixedCallback(chatId, messageId, callbackQueryId, state, data); }
        }
        return true;
    }

    private boolean onPrefixedCallback(long chatId, Integer messageId, String callbackQueryId,
                                       ConversationState state, String data) {
        if (data.startsWith("orientation:")) {
            ackSilently(callbackQueryId);
            handleOrientationChoice(chatId, messageId, state, data.substring("orientation:".length()));
        } else if (data.startsWith("gender:")) {
            ackSilently(callbackQueryId);
            handleGenderChoice(chatId, messageId, state, data.substring("gender:".length()));
        } else if (data.startsWith("scope:")) {
            ackSilently(callbackQueryId);
            handleScopeChoice(chatId, messageId, state, data.substring("scope:".length()));
        } else if (data.startsWith("seek:")) {
            handleSeekingGenderToggle(chatId, messageId, callbackQueryId, state, data.substring("seek:".length()));
        } else if (data.startsWith("photo_manage:remove:")) {
            ackSilently(callbackQueryId);
            handlePhotoManageRemove(chatId, state, Integer.parseInt(data.substring("photo_manage:remove:".length())));
        } else {
            return false;
        }
        return true;
    }

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
                        (state.seekingGenders().contains(gender) ? "✅ " : "") + Labels.humanize(gender),
                        "seek:" + gender))
                .toList();
        return InlineKeyboardMarkup.builder()
                .keyboardRow(options)
                .keyboardRow(List.of(button("➡️ Continue", "seek:continue")))
                .keyboardRow(List.of(button("⬅️ Back", "back")))
                .build();
    }

    private void handleGenderChoice(long chatId, Integer messageId, ConversationState state, String gender) {
        state.setGender(gender);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("Gender: " + Labels.humanize(gender) + " ✅")
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
                .text("Looking for: " + Labels.joinHumanized(state.seekingGenders()) + " ✅")
                .build());
        advanceAfter(chatId, state, ConversationStep.SEEKING_GENDERS);
    }

    private void handleOrientationChoice(long chatId, Integer messageId, ConversationState state, String value) {
        state.setOrientation(value);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("Orientation: " + Labels.humanize(value) + " ✅").build());
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
                : "Manage photo URL references (stored only; not fetched or analyzed):\n" + Labels.numbered(photos);
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

    private void promptSearchScope(long chatId) {
        sendNew(chatId, "Where should I look for matches? You can change this later in Settings.",
                Keyboards.scope("scope:"));
    }

    private void handleScopeChoice(long chatId, Integer messageId, ConversationState state, String scope) {
        if (!Labels.isValidScope(scope)) {
            return;
        }
        state.setSearchScope(scope);
        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("Search scope: " + Labels.humanizeScope(scope) + " ✅").build());
        advanceAfter(chatId, state, ConversationStep.SEARCH_SCOPE);
    }

    private void jumpToEditScope(long chatId, Integer messageId, ConversationState state) {
        state.setReturnToReview(true);
        state.setStep(ConversationStep.SEARCH_SCOPE);
        stateStore.save(state);
        removeKeyboard(chatId, messageId);
        promptSearchScope(chatId);
    }

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

    public void showReview(long chatId, ConversationState state) {
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
                state.name(), state.age(), Labels.humanize(state.gender()), Labels.humanize(state.orientation()),
                state.city(), state.country(),
                Labels.humanizeScope(state.searchScope()),
                state.minPreferredAge() == null ? "not set" : state.minPreferredAge() + "-" + state.maxPreferredAge(),
                Labels.joinHumanized(state.seekingGenders()),
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

        // Replace the collected draft with an idle DONE state: no leftover personal data is kept,
        // and free text from now on is routed to preference refinement.
        ConversationState idle = new ConversationState(telegramUserId);
        idle.setStep(ConversationStep.DONE);
        stateStore.save(idle);

        send(EditMessageText.builder().chatId(chatId).messageId(messageId)
                .text("🎉 Your profile is live!")
                .build());
        menuFlow.sendMainMenu(chatId, telegramUserId);
    }

    // ─────────────────────────────── helpers ───────────────────────────────

    private static InlineKeyboardButton button(String text, String callbackData) {
        return Keyboards.button(text, callbackData);
    }

    private void send(org.telegram.telegrambots.meta.api.methods.BotApiMethod<? extends java.io.Serializable> method) {
        telegram.send(method);
    }

    private void sendNew(long chatId, String text, InlineKeyboardMarkup keyboard) {
        telegram.sendNew(chatId, text, keyboard);
    }

    private void removeKeyboard(long chatId, Integer messageId) {
        telegram.removeKeyboard(chatId, messageId);
    }

    private void ackSilently(String callbackQueryId) {
        telegram.ackSilently(callbackQueryId);
    }

    private void answerCallback(String callbackQueryId, String text, boolean alert) {
        telegram.answerCallback(callbackQueryId, text, alert);
    }
}
