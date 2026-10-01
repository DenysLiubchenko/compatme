package ua.kpi.project.compatme.adapter.out.gemini;

import com.google.genai.Client;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.application.exception.EmbeddingGenerationException;
import ua.kpi.project.compatme.application.port.out.EmbeddingProviderPort;
import ua.kpi.project.compatme.domain.model.EmbeddingVector;
import ua.kpi.project.compatme.domain.service.TextHasher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Outbound adapter implementing {@link EmbeddingProviderPort} via the official
 * {@code com.google.genai} Java SDK, targeting the {@code gemini-embedding-001} model (name
 * configurable via {@link GeminiProperties}).
 *
 * <p>Retries with the delay supplied by Gemini when available, falling back to configured
 * exponential backoff. Retry parameters are externalized via {@link GeminiProperties}.
 */
@Component
public class GeminiEmbeddingAdapter implements EmbeddingProviderPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingAdapter.class);
    private static final Pattern RETRY_DELAY_FIELD = Pattern.compile("\\\"retryDelay\\\"\\s*:\\s*\\\"([0-9]+(?:\\.[0-9]+)?)s\\\"");
    private static final Pattern RETRY_DELAY_MESSAGE = Pattern.compile("Please retry in\\s+([0-9]+(?:\\.[0-9]+)?)s", Pattern.CASE_INSENSITIVE);

    private final Client geminiClient;
    private final GeminiProperties properties;

    public GeminiEmbeddingAdapter(Client geminiClient, GeminiProperties properties) {
        this.geminiClient = geminiClient;
        this.properties = properties;
    }

    @Override
    public EmbeddingVector embed(String text) {
        int maxAttempts = Math.max(1, properties.getMaxRetryAttempts());
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return embedOnce(text);
            } catch (RuntimeException e) {
                lastFailure = e;
                if (attempt == maxAttempts) break;

                Optional<Long> providerDelay = retryDelayMillis(e.getMessage());
                long delayMillis = providerDelay.isPresent()
                        ? providerDelay.get()
                        : fallbackDelayMillis(attempt);
                log.warn("Gemini embedContent call failed for model {} (attempt {}/{}); retrying in {} ms: {}",
                        properties.getEmbeddingModel(), attempt, maxAttempts, delayMillis, e.getMessage());
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    e.addSuppressed(interrupted);
                    throw e;
                }
            }
        }
        throw lastFailure;
    }

    private EmbeddingVector embedOnce(String text) {
        EmbedContentConfig config = EmbedContentConfig.builder()
                .outputDimensionality(properties.getEmbeddingDimensionality())
                .build();

        EmbedContentResponse response = geminiClient.models.embedContent(properties.getEmbeddingModel(), text, config);
        float[] values = extractValues(response);

        return new EmbeddingVector(
                values, properties.getEmbeddingModel(), values.length, TextHasher.sha256Hex(text), Instant.now());
    }

    static Optional<Long> retryDelayMillis(String message) {
        if (message == null) return Optional.empty();
        Matcher fieldMatcher = RETRY_DELAY_FIELD.matcher(message);
        Matcher messageMatcher = RETRY_DELAY_MESSAGE.matcher(message);
        Matcher matcher;
        if (fieldMatcher.find()) {
            matcher = fieldMatcher;
        } else if (messageMatcher.find()) {
            matcher = messageMatcher;
        } else {
            return Optional.empty();
        }
        try {
            double seconds = Double.parseDouble(matcher.group(1));
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > Long.MAX_VALUE / 1000.0) {
                return Optional.empty();
            }
            return Optional.of((long) Math.ceil(seconds * 1000.0));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private long fallbackDelayMillis(int failedAttempt) {
        double delay = properties.getRetryInitialBackoffMillis()
                * Math.pow(properties.getRetryBackoffMultiplier(), failedAttempt - 1);
        return delay >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, (long) delay);
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
