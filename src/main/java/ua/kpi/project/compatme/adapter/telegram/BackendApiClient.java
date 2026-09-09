package ua.kpi.project.compatme.adapter.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client the Telegram bot uses to talk to this backend's own REST API. Deliberately
 * uses only the plain JDK {@link HttpClient} (no Spring dependency) to keep the Telegram adapter
 * a genuinely separate, thin client layer, per the project's requirement that the bot interface
 * not be embedded directly in domain/service logic.
 */
public class BackendApiClient {

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String baseUrl;

    public BackendApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Map<String, Object> createOrUpdateProfile(String telegramUserId, String displayName, String selfDescription, String preferenceDescription) {
        Map<String, Object> body = Map.of(
                "telegramUserId", telegramUserId,
                "displayName", displayName,
                "selfDescription", selfDescription,
                "preferenceDescription", preferenceDescription);
        return postJson("/api/v1/profiles", body);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRecommendations(String profileId, String strategy, int topN) {
        String path = "/api/v1/profiles/%s/recommendations?strategy=%s&topN=%d".formatted(profileId, strategy, topN);
        Map<String, Object> response = getJson(path);
        return (List<Map<String, Object>>) response.getOrDefault("recommendations", List.of());
    }

    public Map<String, Object> refinePreference(String profileId, String message, String strategy, int topN) {
        Map<String, Object> body = Map.of("message", message, "strategy", strategy, "topN", topN);
        return postJson("/api/v1/profiles/%s/preference-refinements".formatted(profileId), body);
    }

    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readValue(response.body(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call backend API: " + path, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readValue(response.body(), Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call backend API: " + path, e);
        }
    }
}
