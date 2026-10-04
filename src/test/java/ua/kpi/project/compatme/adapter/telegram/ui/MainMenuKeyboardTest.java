package ua.kpi.project.compatme.adapter.telegram.ui;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;

import static org.assertj.core.api.Assertions.assertThat;

class MainMenuKeyboardTest {

    @Test
    void build_isPersistentAndResized() {
        ReplyKeyboardMarkup keyboard = MainMenuKeyboard.build();

        assertThat(keyboard.getIsPersistent()).isTrue();
        assertThat(keyboard.getResizeKeyboard()).isTrue();
        assertThat(keyboard.getKeyboard()).hasSize(2);
        assertThat(keyboard.getKeyboard().stream().flatMap(row -> row.stream()).map(b -> b.getText()))
                .containsExactly(MainMenuKeyboard.BROWSE, MainMenuKeyboard.WHO_LIKED_ME,
                        MainMenuKeyboard.MY_PROFILE, MainMenuKeyboard.HELP);
    }

    @Test
    void match_mapsEveryLabelToItsAction() {
        assertThat(MainMenuKeyboard.match(MainMenuKeyboard.BROWSE)).contains(MenuAction.BROWSE);
        assertThat(MainMenuKeyboard.match(MainMenuKeyboard.MY_PROFILE)).contains(MenuAction.MY_PROFILE);
        assertThat(MainMenuKeyboard.match(MainMenuKeyboard.WHO_LIKED_ME)).contains(MenuAction.WHO_LIKED_ME);
        assertThat(MainMenuKeyboard.match(" " + MainMenuKeyboard.HELP + " ")).contains(MenuAction.HELP);
    }

    @Test
    void match_ignoresOrdinaryText() {
        assertThat(MainMenuKeyboard.match("Browse")).isEmpty();
        assertThat(MainMenuKeyboard.match(null)).isEmpty();
    }
}
