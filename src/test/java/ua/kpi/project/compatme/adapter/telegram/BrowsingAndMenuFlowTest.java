package ua.kpi.project.compatme.adapter.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationState;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStateStore;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStep;
import ua.kpi.project.compatme.adapter.telegram.ui.MainMenuKeyboard;
import ua.kpi.project.compatme.adapter.telegram.ui.MenuAction;
import ua.kpi.project.compatme.application.port.out.ReverseGeocodingPort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Persistent menu routing and edit-in-place browsing. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrowsingAndMenuFlowTest {

    private static final long CHAT_ID = 42L;
    private static final String USER = "42";

    @Mock
    private AbsSender sender;

    @Mock
    private BackendApiClient backendApiClient;

    @Mock
    private ReverseGeocodingPort reverseGeocodingPort;

    private Map<String, ConversationState> states;
    private List<BotApiMethod<?>> executed;
    private AtomicInteger nextMessageId;
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

        executed = new ArrayList<>();
        nextMessageId = new AtomicInteger(100);
        when(sender.execute(any(SendMessage.class))).thenAnswer(inv -> {
            executed.add(inv.getArgument(0));
            Message message = new Message();
            message.setMessageId(nextMessageId.getAndIncrement());
            return message;
        });
        when(sender.execute(any(EditMessageText.class))).thenAnswer(inv -> {
            executed.add(inv.getArgument(0));
            return null;
        });
        when(sender.execute(any(EditMessageReplyMarkup.class))).thenAnswer(inv -> {
            executed.add(inv.getArgument(0));
            return null;
        });

        when(backendApiClient.getProfileByTelegramUserId(USER)).thenReturn(new HashMap<>(Map.of("id", "me")));
        when(backendApiClient.getRecommendations(anyString(), anyInt())).thenAnswer(inv -> new ArrayList<>(List.of(
                candidate("c1", "Anna"), candidate("c2", "Olga"), candidate("c3", "Kate"))));

        handler = new ConversationFlowHandler(sender, backendApiClient, reverseGeocodingPort, stateStore);
        ConversationState idle = new ConversationState(USER);
        idle.setStep(ConversationStep.DONE);
        states.put(USER, idle);
    }

    private static Map<String, Object> candidate(String id, String name) {
        Map<String, Object> map = new HashMap<>();
        map.put("candidateId", id);
        map.put("displayName", name);
        map.put("age", 25);
        map.put("selfDescription", "Hello, I'm " + name);
        return map;
    }

    private List<SendMessage> sentMessages() {
        return executed.stream().filter(SendMessage.class::isInstance).map(SendMessage.class::cast).toList();
    }

    private List<EditMessageText> edits() {
        return executed.stream().filter(EditMessageText.class::isInstance).map(EditMessageText.class::cast).toList();
    }

    @Test
    void browse_sendsOneCardThenSkipEditsItInPlace() {
        handler.onMenuAction(CHAT_ID, USER, MenuAction.BROWSE);
        assertThat(sentMessages()).hasSize(1);
        assertThat(sentMessages().get(0).getText()).contains("Anna");

        handler.onCallbackQuery(CHAT_ID, USER, 100, "cb1", "browse:skip");
        handler.onCallbackQuery(CHAT_ID, USER, 100, "cb2", "browse:skip");

        assertThat(sentMessages()).hasSize(1); // no new card messages were stacked
        assertThat(edits()).extracting(EditMessageText::getText)
                .anyMatch(t -> t.contains("Olga"))
                .anyMatch(t -> t.contains("Kate"));
        assertThat(edits()).allMatch(e -> e.getMessageId() == 100);
    }

    @Test
    void like_recordsLikeAndEditsCardToNextCandidate() {
        handler.onMenuAction(CHAT_ID, USER, MenuAction.BROWSE);

        handler.onCallbackQuery(CHAT_ID, USER, 100, "cb1", "browse:like");

        verify(backendApiClient).recordLike("me", "c1");
        assertThat(edits()).extracting(EditMessageText::getText).anyMatch(t -> t.contains("Olga"));
        assertThat(sentMessages()).hasSize(1);
    }

    @Test
    void browseAgain_resumesByEditingTrackedCardInsteadOfSendingAnother() {
        handler.onMenuAction(CHAT_ID, USER, MenuAction.BROWSE);
        handler.onCallbackQuery(CHAT_ID, USER, 100, "cb1", "browse:skip"); // now showing Olga

        handler.onMenuAction(CHAT_ID, USER, MenuAction.BROWSE);

        assertThat(sentMessages()).hasSize(1);
        assertThat(edits().get(edits().size() - 1).getText()).contains("Olga");
        verify(backendApiClient).getRecommendations(anyString(), anyInt()); // not re-fetched
    }

    @Test
    void skipPastLastCandidate_editsCardIntoEndOfListMessage() {
        handler.onMenuAction(CHAT_ID, USER, MenuAction.BROWSE);
        for (int i = 0; i < 3; i++) {
            handler.onCallbackQuery(CHAT_ID, USER, 100, "cb" + i, "browse:skip");
        }

        assertThat(edits().get(edits().size() - 1).getText()).contains("That's everyone");
        assertThat(sentMessages()).hasSize(1);
    }

    @Test
    void buttonOnStaleCard_isIgnoredAndItsKeyboardRemoved() {
        handler.onMenuAction(CHAT_ID, USER, MenuAction.BROWSE); // tracked card = 100

        handler.onCallbackQuery(CHAT_ID, USER, 55, "cb1", "browse:like"); // an old card

        verify(backendApiClient, never()).recordLike(anyString(), anyString());
        assertThat(executed).anyMatch(m -> m instanceof EditMessageReplyMarkup e && e.getMessageId() == 55);
    }

    @Test
    void menuLabelDuringOnboardingStep_isNotConsumedAsFormInput() {
        ConversationState state = new ConversationState(USER);
        state.setStep(ConversationStep.NAME);
        states.put(USER, state);

        handler.onMenuAction(CHAT_ID, USER, MenuAction.BROWSE);

        assertThat(states.get(USER).name()).isNull();
        assertThat(states.get(USER).step()).isEqualTo(ConversationStep.NAME);
        assertThat(sentMessages().get(0).getText()).contains("finish the current step");
    }

    @Test
    void help_sendsUsageText() {
        handler.onMenuAction(CHAT_ID, USER, MenuAction.HELP);

        assertThat(sentMessages()).hasSize(1);
        assertThat(sentMessages().get(0).getText()).contains("Browse").contains("Who Liked Me");
    }

    @Test
    void menuCommand_sendsPersistentReplyKeyboard() throws Exception {
        handler.onMenuCommand(CHAT_ID, USER);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(sender, org.mockito.Mockito.atLeastOnce()).execute(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(m -> m.getReplyMarkup() instanceof
                org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup r
                && Boolean.TRUE.equals(r.getIsPersistent())
                && r.getKeyboard().get(0).get(0).getText().equals(MainMenuKeyboard.BROWSE));
    }

    @Test
    void freeTextInDoneState_isRoutedToPreferenceRefinement() {
        when(backendApiClient.refinePreference(anyString(), anyString(), anyInt()))
                .thenReturn(Map.of("changeSummary", "calmer", "recommendations", List.of()));

        handler.onTextMessage(CHAT_ID, USER, "I want someone calmer");

        verify(backendApiClient).refinePreference("me", "I want someone calmer", 5);
    }
}
