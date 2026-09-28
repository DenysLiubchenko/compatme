package ua.kpi.project.compatme.adapter.telegram.validation;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileInputValidatorTest {

    @Test
    void isValidName_acceptsNonBlankUnderLimit() {
        assertThat(ProfileInputValidator.isValidName("Maria")).isTrue();
        assertThat(ProfileInputValidator.isValidName("  Maria  ")).isTrue();
    }

    @Test
    void isValidName_rejectsBlankOrTooLong() {
        assertThat(ProfileInputValidator.isValidName(null)).isFalse();
        assertThat(ProfileInputValidator.isValidName("   ")).isFalse();
        assertThat(ProfileInputValidator.isValidName("a".repeat(51))).isFalse();
        assertThat(ProfileInputValidator.isValidName("a".repeat(50))).isTrue();
    }

    @Test
    void parseValidAge_acceptsBoundaryValues() {
        assertThat(ProfileInputValidator.parseValidAge("18")).isEqualTo(OptionalInt.of(18));
        assertThat(ProfileInputValidator.parseValidAge("99")).isEqualTo(OptionalInt.of(99));
        assertThat(ProfileInputValidator.parseValidAge(" 30 ")).isEqualTo(OptionalInt.of(30));
    }

    @Test
    void parseValidAge_rejectsOutOfRangeOrNonNumeric() {
        assertThat(ProfileInputValidator.parseValidAge("17")).isEmpty();
        assertThat(ProfileInputValidator.parseValidAge("100")).isEmpty();
        assertThat(ProfileInputValidator.parseValidAge("not a number")).isEmpty();
        assertThat(ProfileInputValidator.parseValidAge(null)).isEmpty();
        assertThat(ProfileInputValidator.parseValidAge("")).isEmpty();
    }

    @Test
    void isValidLocationText_acceptsNonBlankUnderLimit() {
        assertThat(ProfileInputValidator.isValidLocationText("Kyiv")).isTrue();
        assertThat(ProfileInputValidator.isValidLocationText("   ")).isFalse();
        assertThat(ProfileInputValidator.isValidLocationText("a".repeat(101))).isFalse();
    }

    @Test
    void isValidDescription_enforcesMinimumLength() {
        assertThat(ProfileInputValidator.isValidDescription("too short")).isFalse();
        assertThat(ProfileInputValidator.isValidDescription("short")).isFalse();
        assertThat(ProfileInputValidator.isValidDescription("This is long enough to pass.")).isTrue();
        assertThat(ProfileInputValidator.isValidDescription(null)).isFalse();
        assertThat(ProfileInputValidator.isValidDescription("   ")).isFalse();
    }
}
