package ua.kpi.project.compatme.adapter.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationState;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStateStore;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStep;
import ua.kpi.project.compatme.adapter.telegram.ui.MainMenuKeyboard;
import ua.kpi.project.compatme.adapter.telegram.ui.MenuAction;
import ua.kpi.project.compatme.adapter.telegram.ui.ReplyButtons;
import ua.kpi.project.compatme.adapter.telegram.ui.ReplyKeyboards;
import ua.kpi.project.compatme.application.port.out.ReverseGeocodingPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Everything except Like/Skip cards is a reply keyboard, and its buttons arrive as plain text. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReplyKeyboardFlowTest {

    private static final long CHAT_ID = 42L;
    private static final String USER = "42";

    @Mock
    private AbsSender sender;
    @Mock
    private BackendApiClient backendApiClient;
    @Mock
    private ReverseGeocodingPort reverseGeocodingPort;

    private Map<String, ConversationState> states;
    private List<SendMessage> sent;
    private ConversationFlowHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        states = new HashMap<>();
        ConversationStateStore stateStore = mock(ConversationStateStore.class);
        when(stateStore.loadOrCreate(anyString()))
                .thenAnswer(inv -> states.computeIfAbsent(inv.getArgument(0), ConversationState::new));
        org.mockito.Mockito.doAnswer(inv -> {
            ConversationState state = inv.getArgument(0);
            states.put(state.telegramUserId(), state);
            return null;
        }).when(stateStore).save(any());

        sent = new ArrayList<>();
        when(sender.execute(any(SendMessage.class))).thenAnswer(inv -> {
            sent.add(inv.getArgument(0));
            Message message = new Message();
            message.setMessageId(100 + sent.size());
            return message;
        });
        handler = new ConversationFlowHandler(sender, backendApiClient, reverseGeocodingPort, stateStore);
    }

    private void typeAs(String text) {
        handler.onTextMessage(CHAT_ID, USER, text);
    }

    private ConversationState state() {
        return states.get(USER);
    }

    @Test
    void wizard_runsEntirelyOnReplyKeyboardButtonsAndTypedText() {
        handler.onStartCommand(CHAT_ID, USER);
        typeAs(ReplyKeyboards.CREATE_PROFILE);
        typeAs("Maria");
        typeAs("27");
        typeAs(ReplyKeyboards.FEMALE);
        typeAs(ReplyKeyboards.STRAIGHT);
        typeAs(ReplyKeyboards.MALE);                       // toggles "seek MALE"
        typeAs(ReplyKeyboards.CHECKED_PREFIX + ReplyKeyboards.MALE); // toggles it off again
        typeAs(ReplyKeyboards.MALE);
        typeAs(ReplyKeyboards.CONTINUE);
        typeAs(ReplyKeyboards.ENTER_MANUALLY);
        typeAs("Ukraine");
        typeAs("Kyiv");
        typeAs(ReplyKeyboards.SCOPE_COUNTRY);
        typeAs("24-32");
        typeAs("I love hiking and reading books on weekends.");
        typeAs("Someone calm who enjoys deep conversations.");
        typeAs(ReplyKeyboards.SKIP_PHOTOS);

        assertThat(state().step()).isEqualTo(ConversationStep.REVIEW);
        assertThat(state().gender()).isEqualTo("FEMALE");
        assertThat(state().orientation()).isEqualTo("STRAIGHT");
        assertThat(state().seekingGenders()).containsExactly("MALE");
        assertThat(state().searchScope()).isEqualTo("COUNTRY");
        assertThat(sent).noneMatch(m -> m.getReplyMarkup() instanceof InlineKeyboardMarkup);
    }

    @Test
    void buttonLabelsAreOnlyButtonsWhileTheirKeyboardIsActive_otherwiseTheyAreFreeText() {
        ConversationState state = new ConversationState(USER);
        state.setStep(ConversationStep.NAME);
        states.put(USER, state);

        typeAs("Male"); // a gender label typed as a NAME is just a name

        assertThat(state().name()).isEqualTo("Male");
        assertThat(state().step()).isEqualTo(ConversationStep.AGE);
    }

    @Test
    void back_returnsToPreviousStepWithItsKeyboard() {
        ConversationState state = new ConversationState(USER);
        state.setStep(ConversationStep.ORIENTATION);
        states.put(USER, state);

        typeAs(ReplyKeyboards.BACK);

        assertThat(state().step()).isEqualTo(ConversationStep.GENDER);
        assertThat(sent.get(sent.size() - 1).getReplyMarkup()).isInstanceOf(ReplyKeyboardMarkup.class);
    }

    private static List<String> labels(ReplyKeyboardMarkup markup) {
        return markup.getKeyboard().stream().flatMap(row -> row.stream()).map(b -> b.getText()).toList();
    }

    @Test
    void mainMenu_showsOnlyTheFourMainButtons() {
        when(backendApiClient.getProfileByTelegramUserId(USER)).thenReturn(new HashMap<>(Map.of("id", "me")));
        ConversationState idle = new ConversationState(USER);
        idle.setStep(ConversationStep.DONE);
        states.put(USER, idle);

        handler.onMenuCommand(CHAT_ID, USER);

        ReplyKeyboardMarkup markup = (ReplyKeyboardMarkup) sent.get(sent.size() - 1).getReplyMarkup();
        assertThat(markup.getIsPersistent()).isTrue();
        assertThat(labels(markup)).containsExactly(MainMenuKeyboard.BROWSE, MainMenuKeyboard.WHO_LIKED_ME,
                MainMenuKeyboard.MY_PROFILE, MainMenuKeyboard.HELP);
        assertThat(sent).noneMatch(m -> m.getReplyMarkup() instanceof InlineKeyboardMarkup);
    }

    @Test
    void myProfile_swapsToItsOwnViewWithoutTheMainMenuButtons() {
        when(backendApiClient.getProfileByTelegramUserId(USER)).thenReturn(new HashMap<>(Map.of(
                "id", "me", "displayName", "Maria", "age", 27, "selfDescription", "Hi")));
        ConversationState idle = new ConversationState(USER);
        idle.setStep(ConversationStep.DONE);
        states.put(USER, idle);

        handler.onMenuAction(CHAT_ID, USER, MenuAction.MY_PROFILE);

        ReplyKeyboardMarkup markup = (ReplyKeyboardMarkup) sent.get(sent.size() - 1).getReplyMarkup();
        assertThat(labels(markup)).containsExactly(ReplyKeyboards.EDIT_PROFILE, ReplyKeyboards.PAUSE,
                ReplyKeyboards.DELETE_ACCOUNT, ReplyKeyboards.MAIN_MENU);
    }

    @Test
    void mainMenuButton_returnsFromProfileViewToTheMainMenu() {
        when(backendApiClient.getProfileByTelegramUserId(USER)).thenReturn(new HashMap<>(Map.of("id", "me")));
        ConversationState idle = new ConversationState(USER);
        idle.setStep(ConversationStep.DONE);
        states.put(USER, idle);

        typeAs(ReplyKeyboards.MAIN_MENU);

        ReplyKeyboardMarkup markup = (ReplyKeyboardMarkup) sent.get(sent.size() - 1).getReplyMarkup();
        assertThat(labels(markup)).contains(MainMenuKeyboard.BROWSE, MainMenuKeyboard.HELP);
    }

    @Test
    void editProfile_opensTheFieldEditingViewAndBackReturnsToProfileView() {
        Map<String, Object> profile = new HashMap<>(Map.of("id", "me", "displayName", "Maria", "age", 27,
                "gender", "FEMALE", "orientation", "STRAIGHT", "selfDescription", "Hi there friend",
                "preferenceDescription", "Someone kind", "photoUrns", List.of("profile-photos/maria.jpg")));
        when(backendApiClient.getProfileByTelegramUserId(USER)).thenReturn(profile);
        when(backendApiClient.downloadPhoto("me", "profile-photos/maria.jpg")).thenReturn(new byte[] {1});
        ConversationState idle = new ConversationState(USER);
        idle.setStep(ConversationStep.DONE);
        states.put(USER, idle);

        typeAs(ReplyKeyboards.EDIT_PROFILE);
        assertThat(state().step()).isEqualTo(ConversationStep.REVIEW);
        assertThat(labels((ReplyKeyboardMarkup) sent.get(sent.size() - 1).getReplyMarkup()))
                .contains(ReplyKeyboards.EDIT_NAME, ReplyKeyboards.EDIT_SCOPE, ReplyKeyboards.EDIT_AGE_RANGE);
        assertThat(state().orientation()).isEqualTo("STRAIGHT");
        assertThat(state().photoUrns()).containsExactly("profile-photos/maria.jpg");

        typeAs(ReplyKeyboards.BACK);
        assertThat(state().step()).isEqualTo(ConversationStep.DONE);
        assertThat(labels((ReplyKeyboardMarkup) sent.get(sent.size() - 1).getReplyMarkup()))
                .contains(ReplyKeyboards.MAIN_MENU);
    }

    @Test
    void editingPhotosOnAnExistingProfile_returnsToTheProfileWithoutSavingTheCreationForm() {
        Map<String, Object> profile = new HashMap<>(Map.of("id", "me", "displayName", "Maria", "age", 27,
                "gender", "FEMALE", "orientation", "STRAIGHT", "selfDescription", "Hi there friend",
                "preferenceDescription", "Someone kind", "photoUrns", List.of("profile-photos/maria.jpg")));
        when(backendApiClient.getProfileByTelegramUserId(USER)).thenReturn(profile);
        when(backendApiClient.downloadPhoto("me", "profile-photos/maria.jpg")).thenReturn(new byte[] {1});
        ConversationState idle = new ConversationState(USER);
        idle.setStep(ConversationStep.DONE);
        states.put(USER, idle);

        typeAs(ReplyKeyboards.EDIT_PROFILE);
        typeAs(ReplyKeyboards.EDIT_PHOTOS);
        typeAs(ReplyKeyboards.MANAGE_DONE);

        assertThat(state().step()).isEqualTo(ConversationStep.DONE);
        org.mockito.Mockito.verify(backendApiClient, org.mockito.Mockito.never()).createProfile(
                anyString(), anyString(), any(), anyString(), org.mockito.ArgumentMatchers.nullable(String.class), any(),
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void whoLikedMe_isAMainMenuButton() {
        when(backendApiClient.getProfileByTelegramUserId(USER)).thenReturn(new HashMap<>(Map.of("id", "me")));
        when(backendApiClient.getProfilesWhoLikedMe("me")).thenReturn(List.of());
        ConversationState idle = new ConversationState(USER);
        idle.setStep(ConversationStep.DONE);
        states.put(USER, idle);

        handler.onMenuAction(CHAT_ID, USER, MenuAction.WHO_LIKED_ME);

        org.mockito.Mockito.verify(backendApiClient).getProfilesWhoLikedMe("me");
    }

    @Test
    void persistentMenuIsSentOncePerRunToAnExistingUserOnFirstInteraction() {
        when(backendApiClient.getProfileByTelegramUserId(USER)).thenReturn(new HashMap<>(Map.of("id", "me")));
        when(backendApiClient.refinePreference(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(Map.of("changeSummary", "x", "recommendations", List.of()));
        ConversationState idle = new ConversationState(USER);
        idle.setStep(ConversationStep.DONE);
        states.put(USER, idle);

        typeAs("I want someone calmer");
        typeAs("and funnier please");

        long keyboardMessages = sent.stream()
                .filter(m -> m.getReplyMarkup() instanceof ReplyKeyboardMarkup).count();
        assertThat(keyboardMessages).isEqualTo(1);
    }

    @Test
    void resolver_mapsDynamicLabels() {
        assertThat(ReplyButtons.resolve(ReplyKeyboards.REMOVE_URL_PREFIX + "3", ConversationStep.PHOTO_MANAGE))
                .contains("photo_manage:remove:2");
        assertThat(ReplyButtons.resolve(ReplyKeyboards.ageRangeDefaultLabel(24, 30), ConversationStep.AGE_RANGE))
                .contains("agerange:default");
        assertThat(ReplyButtons.resolve(ReplyKeyboards.SCOPE_CITY, ConversationStep.SEARCH_SCOPE)).contains("scope:CITY");
        assertThat(ReplyButtons.resolve(ReplyKeyboards.SCOPE_CITY, ConversationStep.SETTINGS_SCOPE)).contains("setscope:CITY");
        assertThat(ReplyButtons.resolve(ReplyKeyboards.MALE, ConversationStep.SELF_DESCRIPTION)).isEmpty();
    }
}
