package ua.kpi.project.compatme.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers {@link Profile#matchesLocationScope(Profile, LocationScope)}: GLOBAL is always
 * permissive, COUNTRY/CITY compare against the requester's own location (case-insensitively),
 * and an unset requester location falls back to permissive rather than excluding everyone.
 */
class ProfileLocationScopeTest {

    @Test
    void global_alwaysMatches_regardlessOfLocation() {
        Profile requester = profile("Ukraine", "Kyiv");
        Profile candidate = profile("Poland", "Warsaw");

        assertThat(requester.matchesLocationScope(candidate, LocationScope.GLOBAL)).isTrue();
    }

    @Test
    void country_matches_whenSameCountryDifferentCity() {
        Profile requester = profile("Ukraine", "Kyiv");
        Profile candidate = profile("Ukraine", "Lviv");

        assertThat(requester.matchesLocationScope(candidate, LocationScope.COUNTRY)).isTrue();
    }

    @Test
    void country_doesNotMatch_whenDifferentCountry() {
        Profile requester = profile("Ukraine", "Kyiv");
        Profile candidate = profile("Poland", "Warsaw");

        assertThat(requester.matchesLocationScope(candidate, LocationScope.COUNTRY)).isFalse();
    }

    @Test
    void country_isCaseInsensitive() {
        Profile requester = profile("ukraine", "Kyiv");
        Profile candidate = profile("UKRAINE", "Lviv");

        assertThat(requester.matchesLocationScope(candidate, LocationScope.COUNTRY)).isTrue();
    }

    @Test
    void city_matches_onlyWhenSameCountryAndCity() {
        Profile requester = profile("Ukraine", "Kyiv");
        Profile sameCity = profile("Ukraine", "Kyiv");
        Profile sameCountryDifferentCity = profile("Ukraine", "Lviv");

        assertThat(requester.matchesLocationScope(sameCity, LocationScope.CITY)).isTrue();
        assertThat(requester.matchesLocationScope(sameCountryDifferentCity, LocationScope.CITY)).isFalse();
    }

    @Test
    void rejectsProfile_whenMandatoryCountryOrCityIsMissing() {
        assertThatThrownBy(() -> profile(null, null))
                .isInstanceOf(ua.kpi.project.compatme.domain.exception.InvalidProfileDataException.class);
    }

    private static Profile profile(String country, String city) {
        Instant now = Instant.now();
        return Profile.builder().id(ProfileId.generate()).displayName("name").age(25).gender(Gender.OTHER)
                .orientation(Orientation.OTHER).country(country).city(city).seekingGenders(Set.of())
                .selfDescription("self description").preferenceDescription("preference description")
                .embeddings(ProfileEmbeddings.empty()).createdAt(now).updatedAt(now).build();
    }
}
