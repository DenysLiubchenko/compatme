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
 * {@link MenuAction}. Other views (e.g. the profile menu in {@link ReplyKeyboards}) replace this
 * keyboard while the user is in them and offer a way back to it.
 */
public final class MainMenuKeyboard {

    public static final String BROWSE = "🔍 Browse";
    public static final String WHO_LIKED_ME = "❤️ Who Liked Me";
    public static final String MY_PROFILE = "👤 My Profile";
    public static final String HELP = "❓ Help";

    private MainMenuKeyboard() {
    }

    public static ReplyKeyboardMarkup build() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setSelective(false);
        keyboardMarkup.setIsPersistent(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton(BROWSE));
        row1.add(new KeyboardButton(WHO_LIKED_ME));
        keyboard.add(row1);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton(MY_PROFILE));
        row2.add(new KeyboardButton(HELP));
        keyboard.add(row2);

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    public static Optional<MenuAction> match(String text) {
        if (text == null) {
            return Optional.empty();
        }
        return switch (text.trim()) {
            case BROWSE -> Optional.of(MenuAction.BROWSE);
            case WHO_LIKED_ME -> Optional.of(MenuAction.WHO_LIKED_ME);
            case MY_PROFILE -> Optional.of(MenuAction.MY_PROFILE);
            case HELP -> Optional.of(MenuAction.HELP);
            default -> Optional.empty();
        };
    }
}
