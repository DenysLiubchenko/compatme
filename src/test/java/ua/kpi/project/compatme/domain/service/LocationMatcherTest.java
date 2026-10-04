package ua.kpi.project.compatme.domain.service;

import org.junit.jupiter.api.Test;
import ua.kpi.project.compatme.domain.exception.InvalidProfileDataException;
import ua.kpi.project.compatme.domain.model.Location;
import ua.kpi.project.compatme.domain.model.LocationScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationMatcherTest {

    private final Location requester = new Location("Kyiv", "Ukraine");

    @Test
    void city_matchesOnlyExactCityAndCountry_caseInsensitiveAndTrimmed() {
        assertThat(LocationMatcher.matches(requester, new Location("  kyiv ", "UKRAINE"), LocationScope.CITY)).isTrue();
        assertThat(LocationMatcher.matches(requester, new Location("Lviv", "Ukraine"), LocationScope.CITY)).isFalse();
        // same city name, different country
        assertThat(LocationMatcher.matches(requester, new Location("Kyiv", "Poland"), LocationScope.CITY)).isFalse();
    }

    @Test
    void country_matchesSameCountryRegardlessOfCity() {
        assertThat(LocationMatcher.matches(requester, new Location("Lviv", "ukraine"), LocationScope.COUNTRY)).isTrue();
        assertThat(LocationMatcher.matches(requester, new Location("Kyiv", "Poland"), LocationScope.COUNTRY)).isFalse();
    }

    @Test
    void worldwide_matchesEverything() {
        assertThat(LocationMatcher.matches(requester, new Location("Tokyo", "Japan"), LocationScope.WORLDWIDE)).isTrue();
    }

    @Test
    void location_requiresNonBlankCityAndCountry() {
        assertThatThrownBy(() -> new Location(" ", "Ukraine")).isInstanceOf(InvalidProfileDataException.class);
        assertThatThrownBy(() -> new Location("Kyiv", null)).isInstanceOf(InvalidProfileDataException.class);
    }
}
