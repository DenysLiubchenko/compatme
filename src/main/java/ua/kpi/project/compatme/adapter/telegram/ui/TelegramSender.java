package ua.kpi.project.compatme.adapter.telegram.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.Serializable;

/** Thin, failure-tolerant wrapper around {@link AbsSender} shared by every Telegram flow. */
public class TelegramSender {

    public enum Outcome { OK, NOT_MODIFIED, FAILED }

    private static final Logger log = LoggerFactory.getLogger(TelegramSender.class);

    private final AbsSender sender;

    public TelegramSender(AbsSender sender) {
        this.sender = sender;
    }

    /** Fire-and-forget: failures are logged, never thrown. */
    public void send(BotApiMethod<? extends Serializable> method) {
        tryExecute(method);
    }

    /** Executes and classifies the result, so callers can fall back when an edit is rejected. */
    public Outcome tryExecute(BotApiMethod<? extends Serializable> method) {
        try {
            sender.execute(method);
            return Outcome.OK;
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                return Outcome.NOT_MODIFIED;
            }
            log.warn("Failed to execute Telegram method {}: {}", method.getClass().getSimpleName(), e.getMessage());
            return Outcome.FAILED;
        }
    }

    /** Edits a message's media (photo + caption + keyboard); same outcome classification as {@link #tryExecute}. */
    public Outcome editMedia(EditMessageMedia edit) {
        try {
            sender.execute(edit);
            return Outcome.OK;
        } catch (TelegramApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("message is not modified")) {
                return Outcome.NOT_MODIFIED;
            }
            log.warn("Failed to edit message media: {}", e.getMessage());
            return Outcome.FAILED;
        }
    }

    /** Sends a message and returns its Telegram {@code message_id}, or {@code null} if sending failed. */
    public Integer sendMessage(SendMessage message) {
        try {
            Message sent = sender.execute(message);
            return sent == null ? null : sent.getMessageId();
        } catch (TelegramApiException e) {
            log.warn("Failed to send message: {}", e.getMessage());
            return null;
        }
    }

    /** Sends a photo and returns its {@code message_id}, or {@code null} if sending failed. */
    public Integer sendPhoto(SendPhoto photo) {
        try {
            Message sent = sender.execute(photo);
            return sent == null ? null : sent.getMessageId();
        } catch (TelegramApiException e) {
            log.warn("Failed to send photo: {}", e.getMessage());
            return null;
        }
    }

    public void deleteQuietly(long chatId, Integer messageId) {
        if (messageId != null) {
            tryExecute(DeleteMessage.builder().chatId(chatId).messageId(messageId).build());
        }
    }

    public void sendNew(long chatId, String text, InlineKeyboardMarkup keyboard) {
        send(SendMessage.builder().chatId(chatId).text(text).replyMarkup(keyboard).build());
    }

    public void sendText(long chatId, String text) {
        send(SendMessage.builder().chatId(chatId).text(text).build());
    }

    public void removeKeyboard(long chatId, Integer messageId) {
        send(EditMessageReplyMarkup.builder().chatId(chatId).messageId(messageId)
                .replyMarkup(InlineKeyboardMarkup.builder().build())
                .build());
    }

    public void ackSilently(String callbackQueryId) {
        answerCallback(callbackQueryId, null, false);
    }

    public void answerCallback(String callbackQueryId, String alertText, boolean showAlert) {
        AnswerCallbackQuery.AnswerCallbackQueryBuilder builder =
                AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId);
        if (alertText != null) {
            builder.text(alertText).showAlert(showAlert);
        }
        send(builder.build());
    }
}
