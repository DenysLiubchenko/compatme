package ua.kpi.project.compatme.adapter.out.geocoding;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.adapter.out.geocoding.dto.NominatimReverseGeocodeResponse;
import ua.kpi.project.compatme.application.exception.ReverseGeocodingException;
import ua.kpi.project.compatme.application.port.out.ReverseGeocodingPort;
import ua.kpi.project.compatme.domain.model.LocationResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Outbound adapter implementing {@link ReverseGeocodingPort} via the free Nominatim (OpenStreetMap)
 * reverse-geocoding API. Uses the plain JDK {@link HttpClient} (no SDK exists for this service),
 * mirroring {@code adapter.telegram.BackendApiClient}'s "no extra HTTP framework" style rather
 * than the Gemini adapters' SDK-client style, since there is no such SDK here.
 *
 * <p>Nominatim's usage policy requires (a) a descriptive {@code User-Agent} identifying the
 * application, and (b) at most 1 request/second. Since this is only ever called once per user
 * during onboarding (never a hot path), a simple synchronous call plus a client-side
 * minimum-interval guard ({@link #enforceMinimumInterval()}) is sufficient — no queue or batching
 * is needed. Nothing is cached: each user only geocodes once, so there is no repeated-call cost
 * to optimize away.
 */
@Component
public class NominatimReverseGeocodingAdapter implements ReverseGeocodingPort {

    private static final Logger log = LoggerFactory.getLogger(NominatimReverseGeocodingAdapter.class);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final NominatimProperties properties;
    private final ObjectMapper objectMapper;

    private final Object rateLimitLock = new Object();
    private long lastRequestAtMillis = 0L;

    public NominatimReverseGeocodingAdapter(NominatimProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public LocationResult resolveLocation(double latitude, double longitude) {
        enforceMinimumInterval();
        try {
            String uri = "%s/reverse?format=jsonv2&lat=%s&lon=%s&accept-language=en&zoom=10".formatted(
                    properties.getBaseUrl(),
                    Double.toString(latitude),
                    Double.toString(longitude));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("User-Agent", properties.getUserAgent())
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ReverseGeocodingException(
                        "Nominatim returned HTTP " + response.statusCode() + " for (" + latitude + ", " + longitude + ")", null);
            }

            NominatimReverseGeocodeResponse parsed =
                    objectMapper.readValue(response.body(), NominatimReverseGeocodeResponse.class);
            if (parsed.address() == null || parsed.address().country() == null) {
                throw new ReverseGeocodingException(
                        "Nominatim found no address for (" + latitude + ", " + longitude + ")", null);
            }

            String city = parsed.address().bestCityGuess();
            return new LocationResult(parsed.address().country(), city);
        } catch (ReverseGeocodingException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Nominatim reverse-geocode call failed for ({}, {}): {}", latitude, longitude, e.getMessage());
            throw new ReverseGeocodingException("Failed to reverse-geocode coordinates", e);
        }
    }

    /**
     * Blocks the calling thread just long enough to respect Nominatim's 1 request/second usage
     * policy. Safe to call from multiple threads (synchronized on a private lock) even though, in
     * practice, onboarding calls this at most once per user at a time.
     */
    private void enforceMinimumInterval() {
        synchronized (rateLimitLock) {
            long elapsed = System.currentTimeMillis() - lastRequestAtMillis;
            long waitMillis = properties.getMinRequestIntervalMillis() - elapsed;
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestAtMillis = System.currentTimeMillis();
        }
    }
}
