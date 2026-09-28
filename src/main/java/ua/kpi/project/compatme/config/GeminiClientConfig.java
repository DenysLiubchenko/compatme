package ua.kpi.project.compatme.config;

import com.google.genai.Client;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.kpi.project.compatme.adapter.out.gemini.GeminiProperties;

/**
 * Wires the Google Gen AI SDK {@link Client} as a Spring bean, configured from
 * {@link GeminiProperties} (bound from {@code application.yml} / environment variables — the API
 * key is never hardcoded). Both {@link ua.kpi.project.compatme.adapter.out.gemini.GeminiEmbeddingAdapter}
 * and {@link ua.kpi.project.compatme.adapter.out.gemini.GeminiChatCompletionAdapter} share this
 * single client instance.
 *
 * <p>{@code geminiProperties} is deliberately declared via a {@code @Bean} method (not
 * {@code @EnableConfigurationProperties}) so it's registered under exactly that bean name —
 * required because {@code GeminiEmbeddingAdapter}/{@code GeminiChatCompletionAdapter}'s
 * {@code @Retryable} annotations reference it by name via SpEL
 * ({@code #{@geminiProperties.maxRetryAttempts}}), which fails to resolve if the bean ends up
 * registered under a different auto-generated name.
 */
@Configuration
public class GeminiClientConfig {

    @Bean
    @ConfigurationProperties(prefix = "gemini")
    public GeminiProperties geminiProperties() {
        return new GeminiProperties();
    }

    @Bean
    public Client geminiClient(GeminiProperties properties) {
        return Client.builder().apiKey(properties.getApiKey()).build();
    }
}
