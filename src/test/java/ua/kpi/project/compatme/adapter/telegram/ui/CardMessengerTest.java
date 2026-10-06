package ua.kpi.project.compatme.adapter.telegram.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ua.kpi.project.compatme.adapter.telegram.BackendApiClient;
import ua.kpi.project.compatme.adapter.telegram.session.BrowsingSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardMessengerTest {

    private static final long CHAT_ID = 42L;

    @Mock
    private AbsSender sender;

    @Mock
    private BackendApiClient backend;

    private CardMessenger cards;
    private BrowsingSession session;

    @BeforeEach
    void setUp() {
        cards = new CardMessenger(new TelegramSender(sender), backend);
        session = new BrowsingSession();
    }

    private static Message message(int id) {
        Message message = new Message();
        message.setMessageId(id);
        return message;
    }

    @Test
    void show_withoutTrackedMessage_sendsNewCardAndTracksIt() throws Exception {
        when(sender.execute(any(SendMessage.class))).thenReturn(message(100));

        cards.show(CHAT_ID, session, Card.text("first", null));

        assertThat(session.cardMessageId()).isEqualTo(100);
        assertThat(session.cardHasPhoto()).isFalse();
    }

    @Test
    void show_withTrackedTextMessage_editsInPlaceInsteadOfSending() throws Exception {
        session.trackCard(100, false);

        cards.show(CHAT_ID, session, Card.text("second", null));

        ArgumentCaptor<EditMessageText> edit = ArgumentCaptor.forClass(EditMessageText.class);
        verify(sender).execute(edit.capture());
        assertThat(edit.getValue().getMessageId()).isEqualTo(100);
        assertThat(edit.getValue().getText()).isEqualTo("second");
        verify(sender, never()).execute(any(SendMessage.class));
        assertThat(session.cardMessageId()).isEqualTo(100);
    }

    @Test
    void show_whenEditFails_deletesOldMessageAndSendsNewOne() throws Exception {
        session.trackCard(100, false);
        when(sender.execute(any(EditMessageText.class))).thenThrow(new TelegramApiException("message can't be edited"));
        when(sender.execute(any(SendMessage.class))).thenReturn(message(101));

        cards.show(CHAT_ID, session, Card.text("next", null));

        ArgumentCaptor<DeleteMessage> delete = ArgumentCaptor.forClass(DeleteMessage.class);
        verify(sender).execute(delete.capture());
        assertThat(delete.getValue().getMessageId()).isEqualTo(100);
        assertThat(session.cardMessageId()).isEqualTo(101);
    }

    @Test
    void show_whenMessageNotModified_keepsTrackedMessageWithoutResending() throws Exception {
        session.trackCard(100, false);
        when(sender.execute(any(EditMessageText.class)))
                .thenThrow(new TelegramApiException("Bad Request: message is not modified"));

        cards.show(CHAT_ID, session, Card.text("same", null));

        verify(sender, never()).execute(any(SendMessage.class));
        verify(sender, never()).execute(any(DeleteMessage.class));
        assertThat(session.cardMessageId()).isEqualTo(100);
    }

    @Test
    void show_photoToPhoto_replacesTheMessageBecauseStoredBytesCannotBeEditedAsMedia() throws Exception {
        session.trackCard(100, true);
        when(backend.downloadPhoto("profile-1", "profile-photos/a.jpg")).thenReturn(new byte[] {1});
        when(sender.execute(any(SendPhoto.class))).thenReturn(message(101));

        cards.show(CHAT_ID, session, new Card("caption", "profile-1", "profile-photos/a.jpg", null));

        verify(sender).execute(any(DeleteMessage.class));
        assertThat(session.cardMessageId()).isEqualTo(101);
        assertThat(session.cardHasPhoto()).isTrue();
    }

    @Test
    void show_textToPhoto_cannotEditInPlace_soDeletesAndSendsPhoto() throws Exception {
        session.trackCard(100, false);
        when(backend.downloadPhoto("profile-1", "profile-photos/a.jpg")).thenReturn(new byte[] {1});
        when(sender.execute(any(SendPhoto.class))).thenReturn(message(102));

        cards.show(CHAT_ID, session, new Card("caption", "profile-1", "profile-photos/a.jpg", null));

        verify(sender).execute(any(DeleteMessage.class));
        assertThat(session.cardMessageId()).isEqualTo(102);
        assertThat(session.cardHasPhoto()).isTrue();
    }

    @Test
    void show_whenPhotoCannotBeSent_fallsBackToTextCard() throws Exception {
        when(sender.execute(any(SendPhoto.class))).thenThrow(new TelegramApiException("wrong file identifier/HTTP URL"));
        when(sender.execute(any(SendMessage.class))).thenReturn(message(103));

        cards.show(CHAT_ID, session, new Card("caption", "profile-1", "profile-photos/a.jpg", null));

        assertThat(session.cardMessageId()).isEqualTo(103);
        assertThat(session.cardHasPhoto()).isFalse();
    }

    @Test
    void truncate_limitsCaptionLength() {
        String longText = "x".repeat(2000);

        assertThat(CardMessenger.truncate(longText, CardMessenger.CAPTION_LIMIT)).hasSize(CardMessenger.CAPTION_LIMIT);
        assertThat(CardMessenger.truncate("short", CardMessenger.CAPTION_LIMIT)).isEqualTo("short");
    }
}
