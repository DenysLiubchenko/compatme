package ua.kpi.project.compatme.adapter.out.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.adapter.out.gemini.dto.PreferenceRefinementLlmResponse;
import ua.kpi.project.compatme.application.exception.ChatCompletionException;
import ua.kpi.project.compatme.application.port.out.ChatCompletionPort;
import ua.kpi.project.compatme.domain.model.PreferenceRefinementResult;

/**
 * Outbound adapter implementing {@link ChatCompletionPort} via the official {@code com.google.genai}
 * Java SDK, targeting a Gemini Flash model (name configurable via {@link GeminiProperties}).
 *
 * <p>Requests a structured JSON response (rather than free-form text) so the rewritten preference
 * description and a short change summary can be reliably parsed and logged — this is more
 * defensible in the thesis methodology chapter than parsing free text with regex/heuristics.
 *
 * <p>The system instruction explicitly tells the model to preserve the user's language (the
 * primary user-facing language of this project is Ukrainian), so refinements phrased in
 * Ukrainian produce a rewritten Ukrainian {@code preferenceDescription}, not a translated one.
 */
@Component
public class GeminiChatCompletionAdapter implements ChatCompletionPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiChatCompletionAdapter.class);

    private static final String SYSTEM_INSTRUCTION = """
            You help refine a dating profile's "who I'm looking for" description based on a
            user's natural-language feedback message. Rewrite the preference description to
            incorporate the requested change while preserving unrelated existing content.
            Always respond in the SAME language as the user's current preference description
            and refinement message (e.g. if they are written in Ukrainian, respond in Ukrainian).
            Return only the requested JSON object, with no additional commentary.
            """;

    private static final Schema RESPONSE_SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(java.util.Map.of(
                    "updatedPreferenceDescription", Schema.builder().type(Type.Known.STRING).build(),
                    "changeSummary", Schema.builder().type(Type.Known.STRING).build()))
            .required(java.util.List.of("updatedPreferenceDescription", "changeSummary"))
            .build();

    private final Client geminiClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiChatCompletionAdapter(Client geminiClient, GeminiProperties properties, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Retryable(
            retryFor = RuntimeException.class,
            maxAttemptsExpression = "#{@geminiProperties.maxRetryAttempts}",
            backoff = @Backoff(
                    delayExpression = "#{@geminiProperties.retryInitialBackoffMillis}",
                    multiplierExpression = "#{@geminiProperties.retryBackoffMultiplier}"))
    public PreferenceRefinementResult interpretPreferenceRefinement(String currentPreferenceDescription, String userMessage) {
        try {
            Content systemInstruction = Content.fromParts(Part.fromText(SYSTEM_INSTRUCTION));
            String prompt = """
                    Current preference description: %s

                    User's refinement request: %s
                    """.formatted(currentPreferenceDescription, userMessage);

            GenerateContentConfig config = GenerateContentConfig.builder()
                    .systemInstruction(systemInstruction)
                    .responseMimeType("application/json")
                    .responseSchema(RESPONSE_SCHEMA)
                    .candidateCount(1)
                    .build();

            GenerateContentResponse response = geminiClient.models.generateContent(properties.getChatModel(), prompt, config);
            String jsonText = response.text();
            if (jsonText == null || jsonText.isBlank()) {
                throw new ChatCompletionException("Gemini chat response contained no text");
            }

            PreferenceRefinementLlmResponse parsed = objectMapper.readValue(jsonText, PreferenceRefinementLlmResponse.class);
            return new PreferenceRefinementResult(parsed.updatedPreferenceDescription(), parsed.changeSummary());
        } catch (ChatCompletionException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gemini generateContent call failed for model {}: {}", properties.getChatModel(), e.getMessage());
            throw new ChatCompletionException("Failed to interpret preference refinement via Gemini chat model", e);
        }
    }
}
