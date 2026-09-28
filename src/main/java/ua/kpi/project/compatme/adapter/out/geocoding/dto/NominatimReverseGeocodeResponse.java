package ua.kpi.project.compatme.adapter.out.geocoding.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Internal DTO matching (the subset we need of) Nominatim's {@code /reverse} JSON response.
 * Never exposed outside {@code adapter.out.geocoding} — the application layer only ever sees
 * {@code LocationResult}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NominatimReverseGeocodeResponse(Address address, String error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Address(
            String city,
            String town,
            String village,
            String municipality,
            String country) {

        /** Nominatim reports the settlement under whichever of these fields is populated. */
        public String bestCityGuess() {
            if (city != null && !city.isBlank()) {
                return city;
            }
            if (town != null && !town.isBlank()) {
                return town;
            }
            if (village != null && !village.isBlank()) {
                return village;
            }
            return municipality;
        }
    }
}
