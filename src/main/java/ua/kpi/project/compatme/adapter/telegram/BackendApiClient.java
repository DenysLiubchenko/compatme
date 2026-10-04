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
            String orientation,
            Set<String> seekingGenders,
            String country,
            String city,
            String selfDescription,
            String preferenceDescription,
            List<String> photoUrns) {
        return createProfile(telegramUserId, displayName, age, gender, orientation, seekingGenders, country, city,
                selfDescription, preferenceDescription, photoUrns, null, null, null);
    }

    /** Same as above, additionally sending the user's default location search scope (CITY/COUNTRY/WORLDWIDE). */
    public Map<String, Object> createProfile(
            String telegramUserId,
            String displayName,
            Integer age,
            String gender,
            String orientation,
            Set<String> seekingGenders,
            String country,
            String city,
            String selfDescription,
            String preferenceDescription,
            List<String> photoUrns,
            String searchScope,
            Integer minPreferredAge,
            Integer maxPreferredAge) {
        Map<String, Object> body = new HashMap<>();
        body.put("telegramUserId", telegramUserId);
        body.put("displayName", displayName);
        body.put("age", age);
        body.put("gender", gender);
        body.put("orientation", orientation);
        body.put("seekingGenders", seekingGenders);
        body.put("country", country);
        body.put("city", city);
        body.put("selfDescription", selfDescription);
        body.put("preferenceDescription", preferenceDescription);
        body.put("photoUrns", photoUrns);
        if (searchScope != null) {
            body.put("searchScope", searchScope);
        }
        if (minPreferredAge != null && maxPreferredAge != null) {
            body.put("minPreferredAge", minPreferredAge);
            body.put("maxPreferredAge", maxPreferredAge);
        }
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
    public List<Map<String, Object>> getRecommendations(String profileId, int topN) {
        String path = "/api/v1/profiles/%s/recommendations?topN=%d".formatted(profileId, topN);
        Map<String, Object> response = getJson(path);
        return (List<Map<String, Object>>) response.getOrDefault("recommendations", List.of());
    }

    public Map<String, Object> refinePreference(String profileId, String message, int topN) {
        Map<String, Object> body = Map.of("message", message, "topN", topN);
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

    /**
     * Updates only the default search scope of an existing profile: reads it via {@code GET} and
     * re-submits it through the existing {@code PUT /api/v1/profiles/{id}} with the new scope.
     */
    @SuppressWarnings("unchecked")
    public void updateSearchScope(String profileId, String searchScope) {
        putProfileOverrides(profileId, Map.of("searchScope", searchScope));
    }

    /** Updates only the preferred match age range of an existing profile (same GET + PUT approach). */
    public void updateAgeRange(String profileId, int minAge, int maxAge) {
        putProfileOverrides(profileId, Map.of("minPreferredAge", minAge, "maxPreferredAge", maxAge));
    }

    private void putProfileOverrides(String profileId, Map<String, Object> overrides) {
        Map<String, Object> body = new HashMap<>(getProfile(profileId));
        body.putAll(overrides);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/profiles/" + profileId))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Backend returned HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to call backend API: PUT /api/v1/profiles/" + profileId, e);
        }
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
