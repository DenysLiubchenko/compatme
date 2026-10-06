package ua.kpi.project.compatme.adapter.telegram.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * Content of a single "current card" message: text (caption when a photo is present), an optional
 * stored photo reference, and its inline keyboard.
 */
public record Card(String text, String profileId, String photoUrn, InlineKeyboardMarkup keyboard) {

    public static Card text(String text, InlineKeyboardMarkup keyboard) {
        return new Card(text, null, null, keyboard);
    }

    public boolean hasPhoto() {
        return photoUrn != null && !photoUrn.isBlank();
    }
}
