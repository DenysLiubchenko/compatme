package ua.kpi.project.compatme.domain.model;

import org.junit.jupiter.api.Test;
import ua.kpi.project.compatme.domain.exception.InvalidProfileDataException;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileRequiredFieldsTest {

    @Test
    void rejectsMissingCountry() {
        assertThatThrownBy(() -> profile().country(null).build())
                .isInstanceOf(InvalidProfileDataException.class)
                .hasMessageContaining("country");
    }

    @Test
    void rejectsMissingCity() {
        assertThatThrownBy(() -> profile().city(" ").build())
                .isInstanceOf(InvalidProfileDataException.class)
                .hasMessageContaining("city");
    }

    @Test
    void rejectsMissingOrientation() {
        assertThatThrownBy(() -> profile().orientation(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("orientation");
    }

    private static Profile.Builder profile() {
        Instant now = Instant.now();
        return Profile.builder().id(ProfileId.generate()).displayName("Name").age(25).gender(Gender.FEMALE)
                .orientation(Orientation.STRAIGHT).country("United States").city("Boston").seekingGenders(Set.of(Gender.MALE))
                .selfDescription("A sufficiently long self description.")
                .preferenceDescription("A sufficiently long preference description.")
                .createdAt(now).updatedAt(now);
    }
}
