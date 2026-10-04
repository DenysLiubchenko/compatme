package ua.kpi.project.compatme.adapter.telegram.ui;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import ua.kpi.project.compatme.adapter.telegram.BackendApiClient;
import ua.kpi.project.compatme.adapter.telegram.session.BrowsingSession;
import ua.kpi.project.compatme.adapter.telegram.ui.TelegramSender.Outcome;

import java.io.ByteArrayInputStream;

/**
 * Owns the "one current card per user" message lifecycle: edit the tracked message in place when
 * possible, otherwise delete it and send a replacement, always keeping the {@link BrowsingSession}
 * pointed at the message that is currently on screen.
 */
public class CardMessenger {

    static final int CAPTION_LIMIT = 1024;
    static final int TEXT_LIMIT = 4096;

    private final TelegramSender telegram;
    private final BackendApiClient backend;

    public CardMessenger(TelegramSender telegram) {
        this(telegram, null);
    }

    public CardMessenger(TelegramSender telegram, BackendApiClient backend) {
        this.telegram = telegram;
        this.backend = backend;
    }

    /**
     * Shows {@code card} in the session's tracked message (edit in place) or, if there is none or
     * the edit is impossible, as a fresh message that becomes the tracked one.
     */
    public void show(long chatId, BrowsingSession session, Card card) {
        Integer target = session.cardMessageId();
        if (target != null) {
            if (edit(chatId, target, session.cardHasPhoto(), card)) {
                session.trackCard(target, card.hasPhoto());
                return;
            }
            telegram.deleteQuietly(chatId, target);
            session.forgetCard();
        }
        sendFresh(chatId, session, card);
    }

    /**
     * Replaces an arbitrary (untracked) message, e.g. a card the user tapped a button on, with
     * {@code card}.
     */
    public void replace(long chatId, Integer messageId, boolean messageHasPhoto, Card card) {
        if (messageId != null) {
            if (edit(chatId, messageId, messageHasPhoto, card)) {
                return;
            }
            telegram.deleteQuietly(chatId, messageId);
        }
        send(chatId, card);
    }

    private void sendFresh(long chatId, BrowsingSession session, Card card) {
        Sent sent = send(chatId, card);
        if (sent == null) {
            session.forgetCard();
        } else {
            session.trackCard(sent.messageId(), sent.photo());
        }
    }

    private record Sent(Integer messageId, boolean photo) {
    }

    private Sent send(long chatId, Card card) {
        if (card.hasPhoto()) {
            InputFile photo = inputFile(card);
            if (photo == null) {
                return sendText(chatId, card);
            }
            Integer photoMessage = telegram.sendPhoto(SendPhoto.builder()
                    .chatId(chatId)
                    .photo(photo)
                    .caption(truncate(card.text(), CAPTION_LIMIT))
                    .replyMarkup(card.keyboard())
                    .build());
            if (photoMessage != null) {
                return new Sent(photoMessage, true);
            }
            // Telegram could not fetch/send the image: degrade to the text-only card.
        }
        return sendText(chatId, card);
    }

    private Sent sendText(long chatId, Card card) {
        Integer textMessage = telegram.sendMessage(SendMessage.builder()
                .chatId(chatId)
                .text(truncate(card.text(), TEXT_LIMIT))
                .replyMarkup(card.keyboard())
                .build());
        return textMessage == null ? null : new Sent(textMessage, false);
    }

    private InputFile inputFile(Card card) {
        if (card.photoUrl() != null && !card.photoUrl().isBlank()) {
            return new InputFile(card.photoUrl());
        }
        if (backend == null || card.profileId() == null || card.photoUrn() == null) {
            return null;
        }
        try {
            byte[] bytes = backend.downloadPhoto(card.profileId(), card.photoUrn());
            return new InputFile(new ByteArrayInputStream(bytes), "profile-photo");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return {@code true} if the message now shows {@code card} (including "not modified"). */
    private boolean edit(long chatId, Integer messageId, boolean currentIsPhoto, Card card) {
        if (currentIsPhoto != card.hasPhoto()) {
            return false; // text <-> photo cannot be edited in place
        }
        Outcome outcome;
        if (card.hasPhoto()) {
            // Telegram can edit media by URL/file-id, but a fresh byte stream is an upload. Replace
            // the message instead so stored photos are sent reliably.
            if (card.photoUrn() != null) {
                return false;
            }
            outcome = telegram.editMedia(EditMessageMedia.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .media(InputMediaPhoto.builder()
                            .media(card.photoUrl())
                            .caption(truncate(card.text(), CAPTION_LIMIT))
                            .build())
                    .replyMarkup(card.keyboard())
                    .build());
        } else {
            outcome = telegram.tryExecute(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(truncate(card.text(), TEXT_LIMIT))
                    .replyMarkup(card.keyboard())
                    .build());
        }
        return outcome != Outcome.FAILED;
    }

    static String truncate(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit - 1) + "…";
    }
}
