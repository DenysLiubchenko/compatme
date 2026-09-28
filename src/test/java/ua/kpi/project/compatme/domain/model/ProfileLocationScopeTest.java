package ua.kpi.project.compatme.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
    void isPermissive_whenRequesterHasNoLocationSet() {
        Profile requesterWithoutLocation = profile(null, null);
        Profile candidate = profile("Poland", "Warsaw");

        assertThat(requesterWithoutLocation.matchesLocationScope(candidate, LocationScope.COUNTRY)).isTrue();
        assertThat(requesterWithoutLocation.matchesLocationScope(candidate, LocationScope.CITY)).isTrue();
    }

    private static Profile profile(String country, String city) {
        Instant now = Instant.now();
        return new Profile(
                ProfileId.generate(), null, "name", 25, Gender.OTHER, Set.of(),
                "self description", "preference description", ProfileEmbeddings.empty(),
                now, now, null, country, city);
    }
}
