package ua.kpi.project.compatme.adapter.telegram.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Content of a single "current card" message: text (caption when a photo is present), an optional
 * photo URL, and its inline keyboard.
 */
public record Card(String text, String photoUrl, InlineKeyboardMarkup keyboard) {

    public static Card text(String text, InlineKeyboardMarkup keyboard) {
        return new Card(text, null, keyboard);
    }

    public boolean hasPhoto() {
        return photoUrl != null && !photoUrl.isBlank();
    }
}
