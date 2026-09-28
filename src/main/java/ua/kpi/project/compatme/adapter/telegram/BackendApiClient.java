package ua.kpi.project.compatme.adapter.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * Creates the final profile from the completed onboarding conversation. Reuses the existing
     * {@code POST /api/v1/profiles} endpoint (backed by {@code ProfileManagementUseCase}) — this
     * client is only responsible for collecting/formatting the HTTP call, never for persistence
     * logic itself.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createProfile(
            String telegramUserId,
            String displayName,
            Integer age,
            String gender,
            Set<String> seekingGenders,
            String country,
            String city,
            String selfDescription,
            String preferenceDescription,
            List<String> photoFileIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("telegramUserId", telegramUserId);
        body.put("displayName", displayName);
        body.put("age", age);
        body.put("gender", gender);
        body.put("seekingGenders", seekingGenders);
        body.put("country", country);
        body.put("city", city);
        body.put("selfDescription", selfDescription);
        body.put("preferenceDescription", preferenceDescription);
        body.put("photoFileIds", photoFileIds);
        return postJson("/api/v1/profiles", body);
    }

    /** Triggers (cached) embedding generation for a just-created/updated profile. */
    public void generateEmbeddings(String profileId) {
        postJson("/api/v1/profiles/%s/embeddings".formatted(profileId), Map.of());
    }

    /** Reuses the existing {@code DELETE /api/v1/profiles/{id}} endpoint — no new deletion logic here. */
    public void deleteProfile(String profileId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/profiles/" + profileId))
                    .DELETE()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call backend API: DELETE /api/v1/profiles/" + profileId, e);
        }
    }

    /** Reuses the existing {@code GET /api/v1/profiles/by-telegram/{telegramUserId}} endpoint. */
    public Map<String, Object> getProfileByTelegramUserId(String telegramUserId) {
        return getJson("/api/v1/profiles/by-telegram/" + telegramUserId);
    }

    /** Reuses the existing {@code GET /api/v1/profiles/{id}} endpoint. */
    public Map<String, Object> getProfile(String profileId) {
        return getJson("/api/v1/profiles/" + profileId);
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

    /** Records a like via {@code POST /api/v1/profiles/{likerId}/likes}; returns whether it created a mutual match. */
    public boolean recordLike(String likerId, String likedProfileId) {
        Map<String, Object> response = postJson(
                "/api/v1/profiles/%s/likes".formatted(likerId), Map.of("likedProfileId", likedProfileId));
        return Boolean.TRUE.equals(response.get("mutualMatch"));
    }

    /** Reuses the existing {@code GET /api/v1/profiles/{id}/liked-by} endpoint for "Who Liked Me". */
    public List<Map<String, Object>> getProfilesWhoLikedMe(String profileId) {
        return getJsonList("/api/v1/profiles/%s/liked-by".formatted(profileId));
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getJsonList(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return objectMapper.readValue(response.body(), List.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call backend API: " + path, e);
        }
    }
}
