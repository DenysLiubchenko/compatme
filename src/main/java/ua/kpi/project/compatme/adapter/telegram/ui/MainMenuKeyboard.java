package ua.kpi.project.compatme.adapter.telegram.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The persistent bottom menu ({@link ReplyKeyboardMarkup}). Pressing a button makes Telegram send
 * its label as a normal text message, so {@link #match(String)} maps incoming text back to a
 * {@link MenuAction}. Sub-menus reuse these two rows and append their own rows below, so the
 * four top-level actions stay visible at all times.
 */
public final class MainMenuKeyboard {

    public static final String BROWSE = "🔍 Browse";
    public static final String MY_PROFILE = "👤 My Profile";
    public static final String PREFERENCES = "⚙️ Preferences";
    public static final String HELP = "❓ Help";

    private MainMenuKeyboard() {
    }

    public static ReplyKeyboardMarkup build() {
        return buildWith(List.of());
    }

    /** The four top-level buttons followed by {@code extraRows} of plain-text buttons. */
    public static ReplyKeyboardMarkup buildWith(List<List<String>> extraRows) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setSelective(false);
        keyboardMarkup.setIsPersistent(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(BROWSE));
        row1.add(new KeyboardButton(MY_PROFILE));
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton(PREFERENCES));
        row2.add(new KeyboardButton(HELP));
        keyboard.add(row2);

        for (List<String> labels : extraRows) {
            KeyboardRow row = new KeyboardRow();
            labels.forEach(label -> row.add(new KeyboardButton(label)));
            keyboard.add(row);
        }

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    public static Optional<MenuAction> match(String text) {
        if (text == null) {
            return Optional.empty();
        }
        return switch (text.trim()) {
            case BROWSE -> Optional.of(MenuAction.BROWSE);
            case MY_PROFILE -> Optional.of(MenuAction.MY_PROFILE);
            case PREFERENCES -> Optional.of(MenuAction.PREFERENCES);
            case HELP -> Optional.of(MenuAction.HELP);
            default -> Optional.empty();
        };
    }
}
