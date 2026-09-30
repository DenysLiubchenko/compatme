package ua.kpi.project.compatme.adapter.out.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.application.port.out.ProfileAttributeExtractionPort;
import ua.kpi.project.compatme.domain.model.DrinkingFrequency;
import ua.kpi.project.compatme.domain.model.DrugUseFrequency;
import ua.kpi.project.compatme.domain.model.OptionalProfileFields;
import ua.kpi.project.compatme.domain.model.RelationshipStatus;
import ua.kpi.project.compatme.domain.model.SmokingStatus;

import java.util.List;
import java.util.Map;

/**
 * Extracts optional profile attributes from the two descriptions only. The prompt explicitly
 * prohibits inference, particularly for sensitive traits; absent or ambiguous values are null or
 * empty. This adapter is not used by the embedding/scoring path.
 */
@Component
public class ProfileAttributeExtractionAdapter implements ProfileAttributeExtractionPort {

    private static final Logger log = LoggerFactory.getLogger(ProfileAttributeExtractionAdapter.class);
    private static final Schema STRING = Schema.builder().type(Type.Known.STRING).nullable(true).build();
    private static final Schema STRING_ARRAY = Schema.builder().type(Type.Known.ARRAY)
            .items(Schema.builder().type(Type.Known.STRING).build()).build();
    private static final Schema RESPONSE_SCHEMA = Schema.builder().type(Type.Known.OBJECT).properties(Map.ofEntries(
            Map.entry("status", STRING), Map.entry("bodyType", STRING), Map.entry("diet", STRING),
            Map.entry("drinks", STRING), Map.entry("drugs", STRING), Map.entry("education", STRING),
            Map.entry("ethnicity", STRING_ARRAY), Map.entry("height", Schema.builder().type(Type.Known.NUMBER).nullable(true).build()),
            Map.entry("income", Schema.builder().type(Type.Known.INTEGER).nullable(true).build()),
            Map.entry("job", STRING), Map.entry("lastOnline", STRING), Map.entry("offspring", STRING),
            Map.entry("pets", STRING), Map.entry("religion", STRING), Map.entry("sign", STRING),
            Map.entry("smokes", STRING), Map.entry("speaks", STRING_ARRAY)))
            .required(List.of("status", "bodyType", "diet", "drinks", "drugs", "education", "ethnicity", "height",
                    "income", "job", "lastOnline", "offspring", "pets", "religion", "sign", "smokes", "speaks"))
            .build();

    private final Client client;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public ProfileAttributeExtractionAdapter(Client client, GeminiProperties properties, ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Retryable(retryFor = RuntimeException.class,
            maxAttemptsExpression = "#{@geminiProperties.maxRetryAttempts}",
            backoff = @Backoff(delayExpression = "#{@geminiProperties.retryInitialBackoffMillis}",
                    multiplierExpression = "#{@geminiProperties.retryBackoffMultiplier}"))
    public OptionalProfileFields extract(String selfDescription, String preferenceDescription) {
        String prompt = """
                Extract profile attributes only when directly and unambiguously stated in these two texts.
                Do not infer any trait from names, writing style, location, stereotypes, or context.
                This includes sensitive attributes such as ethnicity, religion, income, drug use,
                and health-related information. If absent or ambiguous, return null for scalar values
                and [] for arrays. Do not invent values. Return exactly the JSON schema requested.

                About me: %s
                About you / partner preference: %s
                """.formatted(selfDescription, preferenceDescription);
        try {
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json").responseSchema(RESPONSE_SCHEMA).candidateCount(1).build();
            GenerateContentResponse response = client.models.generateContent(properties.getChatModel(), prompt, config);
            if (response.text() == null || response.text().isBlank()) return OptionalProfileFields.empty();
            ExtractedFields fields = objectMapper.readValue(response.text(), ExtractedFields.class);
            return fields.toDomain();
        } catch (Exception e) {
            log.warn("Optional profile-field extraction failed; leaving all optional fields empty: {}", e.getMessage());
            return OptionalProfileFields.empty();
        }
    }

    private record ExtractedFields(String status, String bodyType, String diet, String drinks, String drugs,
                                   String education, List<String> ethnicity, Double height, Integer income,
                                   String job, String lastOnline, String offspring, String pets, String religion,
                                   String sign, String smokes, List<String> speaks) {
        OptionalProfileFields toDomain() {
            return new OptionalProfileFields(enumValue(RelationshipStatus.class, status), bodyType, diet,
                    enumValue(DrinkingFrequency.class, drinks), enumValue(DrugUseFrequency.class, drugs), education,
                    emptyIfNull(ethnicity), height, income, job, null, offspring, pets, religion, sign,
                    enumValue(SmokingStatus.class, smokes), emptyIfNull(speaks));
        }
        private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
            if (value == null || value.isBlank()) return null;
            try { return Enum.valueOf(type, value.trim().toUpperCase().replace(' ', '_')); }
            catch (IllegalArgumentException e) { return null; }
        }
        private static List<String> emptyIfNull(List<String> values) { return values == null ? List.of() : values; }
    }
}
