package ua.kpi.project.compatme.config;

import com.google.genai.Client;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.kpi.project.compatme.adapter.out.gemini.GeminiProperties;

/**
 * Wires the Google Gen AI SDK {@link Client} as a Spring bean, configured from
 * {@link GeminiProperties} (bound from {@code application.yml} / environment variables — the API
 * key is never hardcoded). Both {@link ua.kpi.project.compatme.adapter.out.gemini.GeminiEmbeddingAdapter}
 * and {@link ua.kpi.project.compatme.adapter.out.gemini.GeminiChatCompletionAdapter} share this
 * single client instance.
 */
@Configuration
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiClientConfig {

    @Bean
    public Client geminiClient(GeminiProperties properties) {
        return Client.builder().apiKey(properties.getApiKey()).build();
    }
}
