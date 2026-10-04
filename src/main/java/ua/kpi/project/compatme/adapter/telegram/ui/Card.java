package ua.kpi.project.compatme.adapter.telegram.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Content of a single "current card" message: text (caption when a photo is present), an optional
 * photo URL, and its inline keyboard.
 */
public record Card(String text, String photoUrl, String profileId, String photoUrn, InlineKeyboardMarkup keyboard) {

    public Card(String text, String photoUrl, InlineKeyboardMarkup keyboard) {
        this(text, photoUrl, null, null, keyboard);
    }

    public static Card text(String text, InlineKeyboardMarkup keyboard) {
        return new Card(text, null, null, null, keyboard);
    }

    public boolean hasPhoto() {
        return (photoUrl != null && !photoUrl.isBlank()) || (photoUrn != null && !photoUrn.isBlank());
    }
}
