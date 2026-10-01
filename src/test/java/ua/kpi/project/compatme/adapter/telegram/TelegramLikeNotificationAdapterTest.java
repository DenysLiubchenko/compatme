package ua.kpi.project.compatme.adapter.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramLikeNotificationAdapterTest {

    @Test
    void telegramUserLink_buildsDeepLinkForNumericTelegramId() {
        assertThat(TelegramLikeNotificationAdapter.telegramUserLink("123456789"))
                .contains("tg://user?id=123456789");
    }

    @Test
    void telegramUserLink_rejectsMissingOrInvalidIds() {
        assertThat(TelegramLikeNotificationAdapter.telegramUserLink(null)).isEmpty();
        assertThat(TelegramLikeNotificationAdapter.telegramUserLink("@someone")).isEmpty();
    }
}
