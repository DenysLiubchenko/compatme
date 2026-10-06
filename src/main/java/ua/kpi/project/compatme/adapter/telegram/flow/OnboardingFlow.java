package ua.kpi.project.compatme.adapter.telegram.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import ua.kpi.project.compatme.adapter.telegram.BackendApiClient;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationState;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStateStore;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStep;
import ua.kpi.project.compatme.adapter.telegram.ui.Labels;
import ua.kpi.project.compatme.adapter.telegram.ui.ReplyKeyboards;
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
    private static final int MAX_PHOTOS = 6;

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
        telegram.sendNew(chatId,
                "Welcome to CompatMe! Let's build a profile that finds people who are genuinely compatible with you.",
                ReplyKeyboards.welcome());
    }

    public boolean onText(long chatId, ConversationState state, String text) {
        switch (state.step()) {
            case NAME -> handleNameInput(chatId, state, text);
            case AGE -> handleAgeInput(chatId, state, text);
            case LOCATION_MANUAL_COUNTRY -> handleManualCountryInput(chatId, state, text);
            case LOCATION_MANUAL_CITY -> handleManualCityInput(chatId, state, text);
            case AGE_RANGE -> handleAgeRangeInput(chatId, state, text);
            case SELF_DESCRIPTION -> handleSelfDescriptionInput(chatId, state, text);
            case PREFERENCE_DESCRIPTION -> handlePreferenceDescriptionInput(chatId, state, text);
            case PHOTOS -> handlePhotoTextInput(chatId, state);
            default -> { return false; }
        }
        return true;
    }

    public void onLocationMessage(long chatId, String telegramUserId, double latitude, double longitude) {
        ConversationState state = stateStore.loadOrCreate(telegramUserId);
        if (state.step() != ConversationStep.LOCATION_SHARE_PENDING && state.step() != ConversationStep.LOCATION_CHOICE) {
            return;
        }
        // The shared-location reply keyboard is replaced by the next prompt; hide it meanwhile.
        telegram.sendTextRemovingKeyboard(chatId, "Got it, looking that up...");
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
            telegram.sendTextRemovingKeyboard(chatId,
                    "We couldn't detect your location automatically. Let's enter it manually instead.");
            promptManualCountry(chatId);
        }
    }

    /**
     * Persists the fully collected profile draft, then uploads through the shared REST use case.
     * The final Review save updates this same profile and preserves its storage-owned URNs.
     */
    @SuppressWarnings("unchecked")
    public void onPhotoMessage(long chatId, String telegramUserId, ConversationState state,
                               byte[] bytes, String contentType) {
        try {
            Map<String, Object> profile;
            try {
                profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            } catch (BackendApiClient.BackendApiException e) {
                if (e.statusCode() != 404) {
                    throw e;
                }
                profile = backendApiClient.createProfile(
                        telegramUserId, state.name(), state.age(), state.gender(), state.orientation(),
                        state.seekingGenders(), state.country(), state.city(), state.selfDescription(),
                        state.preferenceDescription(), state.searchScope(),
                        state.minPreferredAge(), state.maxPreferredAge());
            }
            Map<String, Object> result = backendApiClient.uploadPhoto(
                    String.valueOf(profile.get("id")), bytes, contentType);
            Object urn = result.get("urn");
            if (urn instanceof String value && !state.photoUrns().contains(value)) {
                state.addPhotoUrn(value);
                stateStore.save(state);
            }
            sendNew(chatId, "Photo added (" + state.photoUrns().size() + "/" + MAX_PHOTOS + ").",
                    photosKeyboard(state));
        } catch (BackendApiClient.BackendApiException e) {
            sendNew(chatId, e.getMessage(), photosKeyboard(state));
        } catch (Exception e) {
            log.warn("Failed to upload onboarding photo for chat {}: {}", chatId, e.getMessage());
            sendNew(chatId, "I couldn't upload that photo right now. Please try again later.", photosKeyboard(state));
        }
    }

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
            case "photo_manage:done" -> { ackSilently(callbackQueryId); finishPhotoEditing(chatId, state); }
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
        promptText(chatId, "Great! First, what's your name?");
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
        promptText(chatId, "How old are you? (18-99)");
    }

    // ─────────────────────────────── step 3: sex and orientation ───────────────────────────────

    private void promptGender(long chatId) {
        sendNew(chatId, "What is your sex?", ReplyKeyboards.gender());
    }

    private void promptOrientation(long chatId) {
        sendNew(chatId, "What is your orientation?", ReplyKeyboards.orientation());
    }

    // ─────────────────────────────── step 4: seeking genders (multi-select) ───────────────────────────────

    private void promptSeekingGenders(long chatId, ConversationState state) {
        sendNew(chatId, "Who are you interested in meeting? (select all that apply)",
                ReplyKeyboards.seekingGenders(state.seekingGenders()));
    }



    private void handleGenderChoice(long chatId, Integer messageId, ConversationState state, String gender) {
        state.setGender(gender);
        telegram.editIfPresent(chatId, messageId, "Gender: " + Labels.humanize(gender) + " ✅");
        advanceAfter(chatId, state, ConversationStep.GENDER);
    }

    private void handleSeekingGenderToggle(
            long chatId, Integer messageId, String callbackQueryId, ConversationState state, String gender) {
        state.toggleSeekingGender(gender);
        stateStore.save(state);
        // A reply keyboard can only be changed by sending a message, so show the new selection state.
        sendNew(chatId, "Selected: " + Labels.joinHumanized(state.seekingGenders()),
                ReplyKeyboards.seekingGenders(state.seekingGenders()));
        ackSilently(callbackQueryId);
    }

    private void handleSeekingGendersContinue(long chatId, Integer messageId, String callbackQueryId, ConversationState state) {
        if (state.seekingGenders().isEmpty()) {
            telegram.alert(callbackQueryId, chatId, "Please select at least one option first.");
            return;
        }
        ackSilently(callbackQueryId);
        telegram.editIfPresent(chatId, messageId, "Looking for: " + Labels.joinHumanized(state.seekingGenders()) + " ✅");
        advanceAfter(chatId, state, ConversationStep.SEEKING_GENDERS);
    }

    private void handleOrientationChoice(long chatId, Integer messageId, ConversationState state, String value) {
        state.setOrientation(value);
        telegram.editIfPresent(chatId, messageId, "Orientation: " + Labels.humanize(value) + " ✅");
        state.setStep(ConversationStep.SEEKING_GENDERS);
        stateStore.save(state);
        promptSeekingGenders(chatId, state);
    }

    // ─────────────────────────────── step 5: location ───────────────────────────────

    private void promptLocationChoice(long chatId) {
        sendNew(chatId, "How would you like to set your location?", ReplyKeyboards.locationChoice());
    }

    /** Legacy inline "Share" tap: the reply keyboard's own share button is what actually requests the location. */
    private void handleLocationShareChoice(long chatId, Integer messageId, ConversationState state) {
        state.setStep(ConversationStep.LOCATION_SHARE_PENDING);
        stateStore.save(state);
        sendNew(chatId, "Tap the button below to share your location.", ReplyKeyboards.locationChoice());
    }

    private void handleLocationManualChoice(long chatId, Integer messageId, ConversationState state) {
        state.setStep(ConversationStep.LOCATION_MANUAL_COUNTRY);
        stateStore.save(state);
        telegram.editIfPresent(chatId, messageId, "Let's enter your location manually.");
        promptManualCountry(chatId);
    }

    private void promptManualCountry(long chatId) {
        promptText(chatId, "What country are you in?");
    }

    private void promptManualCity(long chatId) {
        promptText(chatId, "What city are you in?");
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
        sendNew(chatId, "📍 Detected: " + result.city() + ", " + result.country() + " — is this correct?",
                ReplyKeyboards.locationConfirm());
    }

    private void handleLocationConfirmYes(long chatId, Integer messageId, ConversationState state) {
        state.confirmPendingLocation();
        if (state.city() == null || state.city().isBlank() || state.country() == null || state.country().isBlank()) {
            state.setStep(ConversationStep.LOCATION_MANUAL_COUNTRY);
            stateStore.save(state);
            telegram.sendTextRemovingKeyboard(chatId, "We couldn't identify both city and country. Please enter them manually.");
            promptManualCountry(chatId);
            return;
        }
        telegram.editIfPresent(chatId, messageId, "📍 Location confirmed: " + state.city() + ", " + state.country());
        advanceAfter(chatId, state, ConversationStep.LOCATION_CONFIRM);
    }

    private void handleLocationConfirmNo(long chatId, Integer messageId, ConversationState state) {
        state.setStep(ConversationStep.LOCATION_MANUAL_COUNTRY);
        stateStore.save(state);
        telegram.editIfPresent(chatId, messageId, "No problem, let's enter it manually.");
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
                .replyMarkup(ReplyKeyboardRemove.builder().removeKeyboard(true).build())
                .build());
    }

    private void promptPreferenceDescription(long chatId) {
        send(SendMessage.builder().chatId(chatId)
                .text("About you: describe the person you are looking for, including any important "
                        + "preferences or deal-breakers. You can mention lifestyle or demographic "
                        + "preferences if relevant. We will not guess or infer anything you don't state. "
                        + "Please write at least 10 characters.")
                .replyMarkup(ReplyKeyboardRemove.builder().removeKeyboard(true).build())
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
        sendNew(chatId, "Would you like to add a photo? (optional, up to " + MAX_PHOTOS
                + "). Send it as a Telegram photo or image document.", photosKeyboard(state));
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard photosKeyboard(ConversationState state) {
        return ReplyKeyboards.photos(state.photoUrns().size(), MAX_PHOTOS);
    }

    private void handlePhotoAddPrompt(long chatId, ConversationState state) {
        state.setStep(ConversationStep.PHOTOS);
        stateStore.save(state);
        promptText(chatId, "Send a photo now (JPEG, PNG, or WebP; maximum 5 MB).");
    }

    private void handlePhotoTextInput(long chatId, ConversationState state) {
        sendNew(chatId, "Please send a photo file, or choose Done/Skip.", photosKeyboard(state));
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
        String text = photos.isEmpty() ? "You haven't added any photos yet."
                : "Manage stored photos: " + photos.size() + "/" + MAX_PHOTOS + " added.";
        sendNew(chatId, text, ReplyKeyboards.photoManage(photos.size(), MAX_PHOTOS));
    }

    private void handlePhotoManageAdd(long chatId, ConversationState state) {
        state.setReturnToReview(true);
        state.setStep(ConversationStep.PHOTOS);
        stateStore.save(state);
        promptText(chatId, "Send a photo now (JPEG, PNG, or WebP; maximum 5 MB).");
    }

    private void handlePhotoManageRemove(long chatId, ConversationState state, int index) {
        if (index >= 0 && index < state.photoUrns().size()) {
            String urn = state.photoUrns().get(index);
            try {
                Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(state.telegramUserId());
                backendApiClient.deletePhoto(String.valueOf(profile.get("id")), urn);
            } catch (BackendApiClient.BackendApiException e) {
                sendNew(chatId, e.getMessage(), ReplyKeyboards.photoManage(state.photoUrns().size(), MAX_PHOTOS));
                return;
            }
        }
        state.removePhotoUrnAt(index);
        stateStore.save(state);
        renderPhotoManageView(chatId, state);
    }

    private void promptSearchScope(long chatId) {
        sendNew(chatId, "Where should I look for matches? You can change this later in Preferences.",
                ReplyKeyboards.scope());
    }

    private void handleScopeChoice(long chatId, Integer messageId, ConversationState state, String scope) {
        if (!Labels.isValidScope(scope)) {
            return;
        }
        state.setSearchScope(scope);
        telegram.editIfPresent(chatId, messageId, "Search scope: " + Labels.humanizeScope(scope) + " ✅");
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



    private void promptAgeRange(long chatId, ConversationState state) {
        int[] range = defaultAgeRange(state.age());
        sendNew(chatId, "What age range should your matches be in? Send it like 25-35, or use the default below.",
                ReplyKeyboards.ageRange(range[0], range[1]));
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
        telegram.editIfPresent(chatId, messageId, "Preferred age range: " + range[0] + "-" + range[1] + " \u2705");
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
        sendNew(chatId, formatReview(state), ReplyKeyboards.review());
    }

    private String formatReview(ConversationState state) {
        String photoLine = state.photoUrns().isEmpty()
                ? "📷 Photos: none added"
                : "📷 Photos: " + state.photoUrns().size() + "/" + MAX_PHOTOS + " added";
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



    private void jumpToEdit(long chatId, Integer messageId, ConversationState state, ConversationStep target) {
        state.setReturnToReview(true);
        state.setStep(target);
        stateStore.save(state);
        removeKeyboard(chatId, messageId);
        switch (target) {
            case NAME -> promptText(chatId, "What's your name?");
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
                promptAge(chatId);
            }
            case ORIENTATION -> {
                state.setStep(ConversationStep.GENDER);
                stateStore.save(state);
                promptGender(chatId);
            }
            case SEEKING_GENDERS -> {
                state.setStep(ConversationStep.ORIENTATION);
                stateStore.save(state);
                promptOrientation(chatId);
            }
            case LOCATION_CHOICE, LOCATION_SHARE_PENDING -> {
                state.setStep(ConversationStep.SEEKING_GENDERS);
                stateStore.save(state);
                promptSeekingGenders(chatId, state);
            }
            case LOCATION_CONFIRM -> {
                state.setStep(ConversationStep.LOCATION_CHOICE);
                stateStore.save(state);
                promptLocationChoice(chatId);
            }
            case REVIEW -> {
                if (menuFlow.hasExistingProfile(state.telegramUserId())) {
                    // Editing an existing profile: Back leaves the edit view for the profile view.
                    state.setStep(ConversationStep.DONE);
                    stateStore.save(state);
                    menuFlow.showOwnProfile(chatId, state.telegramUserId());
                } else {
                    state.setStep(ConversationStep.PREFERENCE_DESCRIPTION);
                    stateStore.save(state);
                    promptPreferenceDescription(chatId);
                }
            }
            case SETTINGS_SCOPE, SETTINGS_AGE_RANGE -> {
                state.setStep(ConversationStep.DONE);
                stateStore.save(state);
                menuFlow.showOwnProfile(chatId, state.telegramUserId());
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
                if (state.isProfileUpdate()) {
                    finishPhotoEditing(chatId, state);
                    return;
                }
                state.setReturnToReview(false);
                showReview(chatId, state);
            }
            default -> log.warn("advanceAfter called for a step with no defined successor: {}", justCompleted);
        }
    }

    /** Photo uploads and removals are persisted immediately for an existing profile. */
    private void finishPhotoEditing(long chatId, ConversationState state) {
        if (state.isProfileUpdate()) {
            state.setStep(ConversationStep.DONE);
            stateStore.save(state);
            menuFlow.showOwnProfile(chatId, state.telegramUserId());
            return;
        }
        showReview(chatId, state);
    }

    private void handleReviewSave(long chatId, Integer messageId, String telegramUserId, ConversationState state) {
        if (!state.isProfileUpdate() && (state.orientation() == null || state.orientation().isBlank())) {
            state.setStep(ConversationStep.ORIENTATION);
            stateStore.save(state);
            telegram.editIfPresent(chatId, messageId, "Please select your orientation before saving.");
            promptOrientation(chatId);
            return;
        }
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

        if (messageId != null) {
            telegram.editIfPresent(chatId, messageId, "🎉 Your profile is live!");
        } else {
            telegram.sendText(chatId, "🎉 Your profile is live!");
        }
        menuFlow.sendMainMenu(chatId, telegramUserId);
    }

    // ─────────────────────────────── helpers ───────────────────────────────

    private void send(org.telegram.telegrambots.meta.api.methods.BotApiMethod<? extends java.io.Serializable> method) {
        telegram.send(method);
    }

    private void sendNew(long chatId, String text, org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard keyboard) {
        telegram.sendNew(chatId, text, keyboard);
    }

    /** Prompt for free-text input: hides whichever reply keyboard was showing. */
    private void promptText(long chatId, String text) {
        telegram.sendTextRemovingKeyboard(chatId, text);
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
