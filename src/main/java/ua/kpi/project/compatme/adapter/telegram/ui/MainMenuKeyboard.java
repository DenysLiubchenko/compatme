package ua.kpi.project.compatme.adapter.telegram.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.List;
import java.util.Optional;

/**
 * The persistent bottom menu ({@link ReplyKeyboardMarkup}). Pressing a button makes Telegram send
 * its label as a normal text message, so {@link #match(String)} maps incoming text back to a
 * {@link MenuAction}.
 */
public final class MainMenuKeyboard {

    public static final String BROWSE = "🔍 Browse";
    public static final String MY_PROFILE = "👤 My Profile";
    public static final String PREFERENCES = "⚙️ Preferences";
    public static final String HELP = "❓ Help";

    private MainMenuKeyboard() {
    }

    public static ReplyKeyboardMarkup build() {
        KeyboardRow first = new KeyboardRow();
        first.add(BROWSE);
        first.add(MY_PROFILE);
        KeyboardRow second = new KeyboardRow();
        second.add(PREFERENCES);
        second.add(HELP);
        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(first, second))
                .resizeKeyboard(true)
                .isPersistent(true)
                .build();
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
