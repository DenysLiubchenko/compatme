package ua.kpi.project.compatme.adapter.telegram.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

/**
 * The ONLY inline keyboards in the bot: per-candidate actions attached to a profile card. Every
 * other keyboard is a reply keyboard (see {@link MainMenuKeyboard} and {@link ReplyKeyboards}).
 */
public final class Keyboards {

    private Keyboards() {
    }

    public static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    public static InlineKeyboardMarkup browseMatches() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("👍 Like", "browse:like"), button("👎 Skip", "browse:skip")))
                .build();
    }

    public static InlineKeyboardMarkup browseLikedMe() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("👍 Like Back", "browse:likeback"), button("➡️ Next", "browse:next")))
                .build();
    }
}
