package ua.kpi.project.compatme.adapter.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationState;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStateStore;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStep;
import ua.kpi.project.compatme.application.port.out.ReverseGeocodingPort;
import ua.kpi.project.compatme.domain.model.LocationResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link ConversationFlowHandler}'s step transitions — including the multi-select
 * seeking-genders toggle, "Back" navigation, and non-destructive edit-and-return-to-Review
 * behavior — without any real Telegram connection. {@link AbsSender}, {@link BackendApiClient},
 * and {@link ReverseGeocodingPort} are mocked; {@link ConversationStateStore} is mocked but backed
 * by a plain in-memory map so state persists across calls exactly like the real Mongo-backed
 * store would within one conversation.
 */
@ExtendWith(MockitoExtension.class)
class ConversationFlowHandlerTest {

    private static final long CHAT_ID = 42L;
    private static final String TELEGRAM_USER_ID = "42";

    @Mock
    private AbsSender sender;

    @Mock
    private BackendApiClient backendApiClient;

    @Mock
    private ReverseGeocodingPort reverseGeocodingPort;

    private ConversationStateStore stateStore;
    private Map<String, ConversationState> statesByUser;
    private ConversationFlowHandler flowHandler;

    @BeforeEach
    void setUp() {
        statesByUser = new HashMap<>();
        stateStore = mock(ConversationStateStore.class);
        when(stateStore.loadOrCreate(anyString()))
                .thenAnswer(inv -> statesByUser.computeIfAbsent(inv.getArgument(0), ConversationState::new));
        org.mockito.Mockito.lenient().doAnswer(inv -> {
            ConversationState state = inv.getArgument(0);
            statesByUser.put(state.telegramUserId(), state);
            return null;
        }).when(stateStore).save(any());

        flowHandler = new ConversationFlowHandler(sender, backendApiClient, reverseGeocodingPort, stateStore);
    }

    private ConversationState currentState() {
        return statesByUser.get(TELEGRAM_USER_ID);
    }

    @Test
    void fullHappyPath_collectsAllFieldsAndReachesReview() {
        // WHEN walking through the entire linear flow
        flowHandler.onStartCommand(CHAT_ID, TELEGRAM_USER_ID);
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 1, "cb1", "start_create");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "Maria");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "27");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 2, "cb2", "gender:FEMALE");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 3, "cb3", "seek:MALE");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 3, "cb4", "seek:continue");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 4, "cb5", "loc:manual");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "Ukraine");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "Kyiv");
        assertThat(currentState().step()).isEqualTo(ConversationStep.SEARCH_SCOPE);
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 6, "cbs", "scope:COUNTRY");
        assertThat(currentState().step()).isEqualTo(ConversationStep.AGE_RANGE);
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "24-32");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "I love hiking and reading books on weekends.");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "Someone calm who enjoys deep conversations.");
        assertThat(currentState().step()).isEqualTo(ConversationStep.PHOTOS);
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 5, "cb6", "photo:skip");

        // THEN every field was collected and the flow stopped at REVIEW (not auto-saved)
        ConversationState state = currentState();
        assertThat(state.step()).isEqualTo(ConversationStep.REVIEW);
        assertThat(state.name()).isEqualTo("Maria");
        assertThat(state.age()).isEqualTo(27);
        assertThat(state.gender()).isEqualTo("FEMALE");
        assertThat(state.seekingGenders()).containsExactly("MALE");
        assertThat(state.country()).isEqualTo("Ukraine");
        assertThat(state.city()).isEqualTo("Kyiv");
        assertThat(state.searchScope()).isEqualTo("COUNTRY");
        assertThat(state.minPreferredAge()).isEqualTo(24);
        assertThat(state.maxPreferredAge()).isEqualTo(32);
        assertThat(state.selfDescription()).isEqualTo("I love hiking and reading books on weekends.");
        assertThat(state.preferenceDescription()).isEqualTo("Someone calm who enjoys deep conversations.");
    }

    @Test
    void invalidAge_reprompts_withoutAdvancingStep() {
        flowHandler.onStartCommand(CHAT_ID, TELEGRAM_USER_ID);
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 1, "cb1", "start_create");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "Maria");

        // WHEN an out-of-range age is submitted
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "5");

        // THEN the step does not advance
        assertThat(currentState().step()).isEqualTo(ConversationStep.AGE);
        assertThat(currentState().age()).isNull();
    }

    @Test
    void backFromGender_returnsToAgeStep() {
        flowHandler.onStartCommand(CHAT_ID, TELEGRAM_USER_ID);
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 1, "cb1", "start_create");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "Maria");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "27");
        assertThat(currentState().step()).isEqualTo(ConversationStep.GENDER);

        // WHEN pressing Back from the GENDER step
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 2, "cb2", "back");

        // THEN it returns to AGE, preserving name/age already collected
        assertThat(currentState().step()).isEqualTo(ConversationStep.AGE);
        assertThat(currentState().name()).isEqualTo("Maria");
        assertThat(currentState().age()).isEqualTo(27);
    }

    @Test
    void seekingGendersToggle_requiresAtLeastOneSelectionBeforeContinuing() {
        flowHandler.onStartCommand(CHAT_ID, TELEGRAM_USER_ID);
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 1, "cb1", "start_create");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "Maria");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "27");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 2, "cb2", "gender:FEMALE");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 3, "cb-orientation", "orientation:STRAIGHT");
        assertThat(currentState().step()).isEqualTo(ConversationStep.SEEKING_GENDERS);

        // WHEN "Continue" is tapped with nothing selected
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 3, "cb3", "seek:continue");

        // THEN the step does not advance
        assertThat(currentState().step()).isEqualTo(ConversationStep.SEEKING_GENDERS);
        assertThat(currentState().seekingGenders()).isEmpty();

        // WHEN a toggle is applied twice (select then deselect)
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 3, "cb4", "seek:MALE");
        assertThat(currentState().seekingGenders()).containsExactly("MALE");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 3, "cb5", "seek:MALE");
        assertThat(currentState().seekingGenders()).isEmpty();

        // WHEN re-selected and continue is tapped
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 3, "cb6", "seek:FEMALE");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 3, "cb7", "seek:continue");

        // THEN it advances
        assertThat(currentState().step()).isEqualTo(ConversationStep.LOCATION_CHOICE);
    }

    @Test
    void editingNameFromReview_updatesOnlyNameAndReturnsDirectlyToReview() {
        // GIVEN a state already sitting at REVIEW with every field collected
        ConversationState state = new ConversationState(TELEGRAM_USER_ID);
        state.setStep(ConversationStep.REVIEW);
        state.setName("Maria");
        state.setAge(27);
        state.setGender("FEMALE");
        state.setOrientation("STRAIGHT");
        state.setOrientation("STRAIGHT");
        state.toggleSeekingGender("MALE");
        state.setCountry("Ukraine");
        state.setCity("Kyiv");
        state.setSelfDescription("Original self description text.");
        state.setPreferenceDescription("Original preference description text.");
        statesByUser.put(TELEGRAM_USER_ID, state);

        // WHEN the user taps "Edit Name" and submits a new name
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 10, "cb1", "review:edit_name");
        assertThat(currentState().step()).isEqualTo(ConversationStep.NAME);

        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "Marichka");

        // THEN only the name changed, everything else survived, and we're back at REVIEW directly
        ConversationState after = currentState();
        assertThat(after.step()).isEqualTo(ConversationStep.REVIEW);
        assertThat(after.name()).isEqualTo("Marichka");
        assertThat(after.age()).isEqualTo(27);
        assertThat(after.gender()).isEqualTo("FEMALE");
        assertThat(after.seekingGenders()).containsExactly("MALE");
        assertThat(after.country()).isEqualTo("Ukraine");
        assertThat(after.city()).isEqualTo("Kyiv");
        assertThat(after.selfDescription()).isEqualTo("Original self description text.");
        assertThat(after.preferenceDescription()).isEqualTo("Original preference description text.");
    }

    @Test
    void locationShare_success_movesToConfirmStepWithoutWritingFinalLocationYet() {
        flowHandler.onStartCommand(CHAT_ID, TELEGRAM_USER_ID);
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 1, "cb1", "start_create");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "Maria");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "27");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 2, "cb2", "gender:FEMALE");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 3, "cb3", "seek:MALE");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 3, "cb4", "seek:continue");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 4, "cb5", "loc:share");
        assertThat(currentState().step()).isEqualTo(ConversationStep.LOCATION_SHARE_PENDING);

        when(reverseGeocodingPort.resolveLocation(50.45, 30.52)).thenReturn(new LocationResult("Ukraine", "Kyiv"));

        // WHEN the client sends its location
        flowHandler.onLocationMessage(CHAT_ID, TELEGRAM_USER_ID, 50.45, 30.52);

        // THEN it's pending confirmation, not yet written as the final country/city
        ConversationState state = currentState();
        assertThat(state.step()).isEqualTo(ConversationStep.LOCATION_CONFIRM);
        assertThat(state.country()).isNull();
        assertThat(state.city()).isNull();
        assertThat(state.pendingCountry()).isEqualTo("Ukraine");
        assertThat(state.pendingCity()).isEqualTo("Kyiv");

        // WHEN confirmed
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 5, "cb6", "loc:confirm_yes");

        // THEN the pending values become final
        assertThat(currentState().country()).isEqualTo("Ukraine");
        assertThat(currentState().city()).isEqualTo("Kyiv");
        assertThat(currentState().step()).isEqualTo(ConversationStep.SEARCH_SCOPE);
    }

    @Test
    void reviewSave_delegatesToBackendApiClientAndClearsState() {
        ConversationState state = new ConversationState(TELEGRAM_USER_ID);
        state.setStep(ConversationStep.REVIEW);
        state.setName("Maria");
        state.setAge(27);
        state.setGender("FEMALE");
        state.toggleSeekingGender("MALE");
        state.setCountry("Ukraine");
        state.setCity("Kyiv");
        state.setSearchScope("CITY");
        state.setPreferredAgeRange(26, 32);
        state.setSelfDescription("Original self description text.");
        state.setPreferenceDescription("Original preference description text.");
        statesByUser.put(TELEGRAM_USER_ID, state);

        when(backendApiClient.createProfile(
                anyString(), anyString(), any(), anyString(), nullable(String.class), any(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), any(), any()))
                .thenReturn(Map.of("id", "profile-123"));

        // WHEN the user confirms save
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 10, "cb1", "review:save");

        // THEN the existing create + embeddings endpoints were called (no duplicated save logic here)
        verify(backendApiClient).createProfile(
                anyString(), anyString(), any(), anyString(), nullable(String.class), any(), anyString(), anyString(), anyString(), anyString(), any(), eq("CITY"), eq(26), eq(32));
        verify(backendApiClient).generateEmbeddings("profile-123");
        verify(stateStore).clear(TELEGRAM_USER_ID);
    }

    @Test
    void settingsScope_updatesSearchScopeOnExistingProfile() {
        when(backendApiClient.getProfileByTelegramUserId(TELEGRAM_USER_ID)).thenReturn(Map.of("id", "profile-123"));

        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 10, "cb1", "setscope:CITY");

        verify(backendApiClient).updateSearchScope("profile-123", "CITY");
    }

    @Test
    void ageRange_invalidInputReprompts_andDefaultButtonUsesAgePlusMinusThree() {
        ConversationState state = new ConversationState(TELEGRAM_USER_ID);
        state.setAge(27);
        state.setStep(ConversationStep.AGE_RANGE);
        statesByUser.put(TELEGRAM_USER_ID, state);

        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "40-30");
        assertThat(currentState().step()).isEqualTo(ConversationStep.AGE_RANGE);
        assertThat(currentState().minPreferredAge()).isNull();

        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 11, "cb1", "agerange:default");
        assertThat(currentState().minPreferredAge()).isEqualTo(24);
        assertThat(currentState().maxPreferredAge()).isEqualTo(30);
        assertThat(currentState().step()).isEqualTo(ConversationStep.SELF_DESCRIPTION);
    }

    @Test
    void settingsAgeRange_updatesExistingProfile() {
        when(backendApiClient.getProfileByTelegramUserId(TELEGRAM_USER_ID)).thenReturn(Map.of("id", "profile-123"));
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 10, "cb1", "settings:agerange");
        assertThat(currentState().step()).isEqualTo(ConversationStep.SETTINGS_AGE_RANGE);

        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "30-40");

        verify(backendApiClient).updateAgeRange("profile-123", 30, 40);
        assertThat(currentState().step()).isEqualTo(ConversationStep.SETTINGS_MENU);
    }

    @Test
    void photoUrlEntry_entersPhotosStepAndEnforcesFiveReferences() {
        flowHandler.onStartCommand(CHAT_ID, TELEGRAM_USER_ID);
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 1, "cb1", "start_create");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "Maria");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "27");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 2, "cb2", "gender:FEMALE");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 3, "cb3", "seek:MALE");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 3, "cb4", "seek:continue");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 4, "cb5", "loc:manual");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "Ukraine");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "Kyiv");
        assertThat(currentState().step()).isEqualTo(ConversationStep.SEARCH_SCOPE);
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 6, "cbs", "scope:COUNTRY");
        assertThat(currentState().step()).isEqualTo(ConversationStep.AGE_RANGE);
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "24-32");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "I love hiking and reading books on weekends.");
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "Someone calm who enjoys deep conversations.");
        assertThat(currentState().step()).isEqualTo(ConversationStep.PHOTOS);

        // WHEN six valid URLs are entered, only five are retained.
        for (int i = 1; i <= 6; i++) {
            flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "https://example.com/photo-" + i + ".jpg");
        }

        assertThat(currentState().photoUrns()).hasSize(5).containsExactly(
                "https://example.com/photo-1.jpg", "https://example.com/photo-2.jpg", "https://example.com/photo-3.jpg",
                "https://example.com/photo-4.jpg", "https://example.com/photo-5.jpg");

        // WHEN done is tapped
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 6, "cb7", "photo:done");

        // THEN it proceeds to Review with all 5 photos intact
        assertThat(currentState().step()).isEqualTo(ConversationStep.REVIEW);
        assertThat(currentState().photoUrns()).hasSize(5);
    }

    @Test
    void photoUrlFreeText_isNotStored_whenNotInPhotosStep() {
        flowHandler.onStartCommand(CHAT_ID, TELEGRAM_USER_ID);

        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "https://example.com/photo.jpg");

        assertThat(currentState().photoUrns()).isEmpty();
    }

    @Test
    void editingPhotosFromReview_removeAndAddMore_thenReturnsDirectlyToReview() {
        // GIVEN a completed profile at REVIEW with 2 existing photos
        ConversationState state = new ConversationState(TELEGRAM_USER_ID);
        state.setStep(ConversationStep.REVIEW);
        state.setName("Maria");
        state.setAge(27);
        state.setGender("FEMALE");
        state.toggleSeekingGender("MALE");
        state.setCountry("Ukraine");
        state.setCity("Kyiv");
        state.setSelfDescription("Original self description text.");
        state.setPreferenceDescription("Original preference description text.");
        state.addPhotoUrn("https://example.com/old-1.jpg");
        state.addPhotoUrn("https://example.com/old-2.jpg");
        statesByUser.put(TELEGRAM_USER_ID, state);

        // WHEN entering photo management and removing the first photo
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 10, "cb1", "review:edit_photos");
        assertThat(currentState().step()).isEqualTo(ConversationStep.PHOTO_MANAGE);
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 11, "cb2", "photo_manage:remove:0");

        assertThat(currentState().photoUrns()).containsExactly("https://example.com/old-2.jpg");

        // WHEN adding another photo and finishing
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 12, "cb3", "photo_manage:add");
        assertThat(currentState().step()).isEqualTo(ConversationStep.PHOTOS);
        flowHandler.onTextMessage(CHAT_ID, TELEGRAM_USER_ID, "https://example.com/new-1.jpg");
        flowHandler.onCallbackQuery(CHAT_ID, TELEGRAM_USER_ID, 13, "cb4", "photo:done");

        // THEN it returns directly to Review with the updated photo set, other fields untouched
        ConversationState after = currentState();
        assertThat(after.step()).isEqualTo(ConversationStep.REVIEW);
        assertThat(after.photoUrns()).containsExactly("https://example.com/old-2.jpg", "https://example.com/new-1.jpg");
        assertThat(after.name()).isEqualTo("Maria");
        assertThat(after.selfDescription()).isEqualTo("Original self description text.");
    }
}
