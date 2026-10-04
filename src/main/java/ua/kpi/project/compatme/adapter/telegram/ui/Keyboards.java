package ua.kpi.project.compatme.adapter.telegram.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

/** Inline keyboards shared by several flows. Per-candidate actions stay inline on purpose. */
public final class Keyboards {

    private Keyboards() {
    }

    public static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    public static InlineKeyboardMarkup backToMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("⬅️ Back to Menu", "menu:back")))
                .build();
    }

    public static InlineKeyboardMarkup browseMatches() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("👍 Like", "browse:like"), button("👎 Skip", "browse:skip")))
                .keyboardRow(List.of(button("⬅️ Back to Menu", "menu:back")))
                .build();
    }

    public static InlineKeyboardMarkup browseLikedMe() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("👍 Like Back", "browse:likeback")))
                .keyboardRow(List.of(button("➡️ Next", "browse:next")))
                .keyboardRow(List.of(button("⬅️ Back to Menu", "menu:back")))
                .build();
    }

    public static InlineKeyboardMarkup ownProfile() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("✏️ Edit My Profile", "profile:edit")))
                .keyboardRow(List.of(button("⬅️ Back to Menu", "menu:back")))
                .build();
    }

    public static InlineKeyboardMarkup mainMenu() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("👤 My Profile", "menu:profile")))
                .keyboardRow(List.of(button("💘 My Matches", "menu:matches")))
                .keyboardRow(List.of(button("❤️ Who Liked Me", "menu:liked")))
                .keyboardRow(List.of(button("⚙️ Settings", "menu:settings")))
                .build();
    }

    public static InlineKeyboardMarkup scope(String prefix) {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("🏙 My city", prefix + "CITY")))
                .keyboardRow(List.of(button("🌍 My country", prefix + "COUNTRY")))
                .keyboardRow(List.of(button("🌐 Worldwide", prefix + "WORLDWIDE")))
                .build();
    }

    public static InlineKeyboardMarkup settings() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("✏️ Edit My Profile", "settings:edit")))
                .keyboardRow(List.of(button("🌍 Search Scope", "settings:scope")))
                .keyboardRow(List.of(button("🎯 Match Age Range", "settings:agerange")))
                .keyboardRow(List.of(button("🔕 Pause Matching", "settings:pause")))
                .keyboardRow(List.of(button("🗑 Delete My Account", "settings:delete")))
                .build();
    }

    public static InlineKeyboardMarkup preferences() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(button("📝 Describe who I'm looking for", "post:prefs")))
                .keyboardRow(List.of(button("🌍 Search scope", "settings:scope")))
                .keyboardRow(List.of(button("🎯 Match age range", "settings:agerange")))
                .build();
    }
}
