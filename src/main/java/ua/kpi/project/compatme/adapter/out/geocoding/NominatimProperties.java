package ua.kpi.project.compatme.adapter.out.geocoding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration for the Nominatim (OpenStreetMap) reverse-geocoding adapter. Bound
 * from {@code application.yml} under the {@code nominatim} prefix.
 */
@ConfigurationProperties(prefix = "nominatim")
public class NominatimProperties {

    /** Base URL of the Nominatim reverse-geocoding endpoint (no path/query). */
    private String baseUrl = "https://nominatim.openstreetmap.org";

    /**
     * Nominatim's usage policy requires a descriptive User-Agent identifying the application —
     * requests without one may be blocked. Must NOT be a generic/default HTTP client string.
     */
    private String userAgent = "CompatMe-Thesis-Project/1.0 (university thesis, non-commercial)";

    /**
     * Minimum milliseconds between consecutive requests to this adapter, enforcing Nominatim's
     * 1 request/second usage-policy limit. Onboarding only calls this once per user, so a simple
     * client-side guard (no queue/batching) is sufficient — see
     * {@link NominatimReverseGeocodingAdapter}.
     */
    private long minRequestIntervalMillis = 1100L;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public long getMinRequestIntervalMillis() {
        return minRequestIntervalMillis;
    }

    public void setMinRequestIntervalMillis(long minRequestIntervalMillis) {
        this.minRequestIntervalMillis = minRequestIntervalMillis;
    }
}
