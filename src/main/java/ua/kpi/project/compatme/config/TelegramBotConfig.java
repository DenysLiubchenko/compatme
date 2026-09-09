package ua.kpi.project.compatme.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.kpi.project.compatme.adapter.telegram.BackendApiClient;
import ua.kpi.project.compatme.adapter.telegram.CompatmeTelegramBot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * Wires the Telegram bot client. Gated behind {@code telegram.enabled=true} so the backend can
 * run (and be tested) without a Telegram bot token configured. The bot itself is a thin adapter
 * that only calls this backend's own REST API via {@link BackendApiClient} — it never touches
 * the domain, application, or persistence layers directly.
 */
@Configuration
@EnableConfigurationProperties(TelegramBotProperties.class)
public class TelegramBotConfig {

    @Bean
    public BackendApiClient backendApiClient(TelegramBotProperties properties) {
        return new BackendApiClient(properties.getBackendBaseUrl());
    }

    @Bean
    @ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
    public CompatmeTelegramBot compatmeTelegramBot(TelegramBotProperties properties, BackendApiClient backendApiClient)
            throws TelegramApiException {
        CompatmeTelegramBot bot = new CompatmeTelegramBot(properties.getBotToken(), properties.getBotUsername(), backendApiClient);
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);
        return bot;
    }
}
