package ua.kpi.project.compatme.adapter.out.gemini;

import com.google.genai.Client;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.application.exception.EmbeddingGenerationException;
import ua.kpi.project.compatme.application.port.out.EmbeddingProviderPort;
import ua.kpi.project.compatme.domain.model.EmbeddingVector;
import ua.kpi.project.compatme.domain.service.TextHasher;

import java.time.Instant;
import java.util.List;

/**
 * Outbound adapter implementing {@link EmbeddingProviderPort} via the official
 * {@code com.google.genai} Java SDK, targeting the {@code gemini-embedding-001} model (name
 * configurable via {@link GeminiProperties}).
 *
 * <p>Retries with exponential backoff on transient failures (e.g. HTTP 429 rate limiting) using
 * Spring Retry. The backoff parameters are themselves externalized via {@link GeminiProperties}
 * so they can be tuned without a code change.
 */
@Component
public class GeminiEmbeddingAdapter implements EmbeddingProviderPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingAdapter.class);

    private final Client geminiClient;
    private final GeminiProperties properties;

    public GeminiEmbeddingAdapter(Client geminiClient, GeminiProperties properties) {
        this.geminiClient = geminiClient;
        this.properties = properties;
    }

    @Override
    @Retryable(
            retryFor = RuntimeException.class,
            maxAttemptsExpression = "#{@geminiProperties.maxRetryAttempts}",
            backoff = @Backoff(
                    delayExpression = "#{@geminiProperties.retryInitialBackoffMillis}",
                    multiplierExpression = "#{@geminiProperties.retryBackoffMultiplier}"))
    public EmbeddingVector embed(String text) {
        try {
            EmbedContentConfig config = EmbedContentConfig.builder()
                    .outputDimensionality(properties.getEmbeddingDimensionality())
                    .build();

            EmbedContentResponse response = geminiClient.models.embedContent(properties.getEmbeddingModel(), text, config);
            float[] values = extractValues(response);

            return new EmbeddingVector(
                    values, properties.getEmbeddingModel(), values.length, TextHasher.sha256Hex(text), Instant.now());
        } catch (RuntimeException e) {
            log.warn("Gemini embedContent call failed for model {}: {}", properties.getEmbeddingModel(), e.getMessage());
            throw e; // rethrown so @Retryable can retry; if attempts are exhausted Spring Retry re-throws this
        }
    }

    @Override
    public String modelName() {
        return properties.getEmbeddingModel();
    }

    private float[] extractValues(EmbedContentResponse response) {
        return response.embeddings()
                .flatMap(embeddings -> embeddings.stream().findFirst())
                .flatMap(contentEmbedding -> contentEmbedding.values())
                .map(this::toFloatArray)
                .orElseThrow(() -> new EmbeddingGenerationException(
                        "Gemini embedContent response contained no embedding values", null));
    }

    private float[] toFloatArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
