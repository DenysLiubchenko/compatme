package ua.kpi.project.compatme.application.port.out;

import ua.kpi.project.compatme.domain.model.LocationResult;

/**
 * Outbound port for resolving GPS coordinates (as shared by a Telegram client's native
 * location-sharing capability) into a country/city pair. Implemented by
 * {@code adapter.out.geocoding} (Nominatim/OpenStreetMap today) — the Telegram adapter depends
 * only on this interface, never on the concrete geocoding provider, mirroring how
 * {@code EmbeddingProviderPort}/{@code ChatCompletionPort} decouple the rest of the app from
 * Gemini specifically.
 */
public interface ReverseGeocodingPort {

    /**
     * @throws ua.kpi.project.compatme.application.exception.ReverseGeocodingException if the
     *     coordinates could not be resolved (network error, no result found)
     */
    LocationResult resolveLocation(double latitude, double longitude);
}
