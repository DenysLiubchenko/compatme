package ua.kpi.project.compatme.domain.model;

import org.junit.jupiter.api.Test;
import ua.kpi.project.compatme.domain.exception.InvalidProfileDataException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgeRangeTest {

    @Test
    void contains_isInclusiveOnBothBounds() {
        AgeRange range = new AgeRange(25, 31);
        assertThat(range.contains(25)).isTrue();
        assertThat(range.contains(31)).isTrue();
        assertThat(range.contains(32)).isFalse();
    }

    @Test
    void rejectsInvertedOrOutOfBoundsRange() {
        assertThatThrownBy(() -> new AgeRange(30, 25)).isInstanceOf(InvalidProfileDataException.class);
        assertThatThrownBy(() -> new AgeRange(17, 30)).isInstanceOf(InvalidProfileDataException.class);
        assertThatThrownBy(() -> new AgeRange(20, 121)).isInstanceOf(InvalidProfileDataException.class);
    }
}
