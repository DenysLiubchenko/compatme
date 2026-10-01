package ua.kpi.project.compatme.bootstrap;

import org.junit.jupiter.api.Test;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Orientation;

import static org.assertj.core.api.Assertions.assertThat;

class OkCupidProfileDataLoaderTest {

    @Test
    void parseCsvLine_preservesQuotedCommasAndEscapedQuotes() {
        assertThat(OkCupidProfileDataLoader.parseCsvLine("22,\"asian, white\",\"said \"\"hello\"\"\""))
                .containsExactly("22", "asian, white", "said \"hello\"");
    }

    @Test
    void readCsvRecord_keepsEmbeddedNewlinesInsideQuotedEssay() throws Exception {
        try (var reader = new java.io.BufferedReader(new java.io.StringReader("1,\"first line\nsecond line\",x\r\n2,y,z\r\n"))) {
            assertThat(OkCupidProfileDataLoader.readCsvRecord(reader)).isEqualTo("1,\"first line\nsecond line\",x");
            assertThat(OkCupidProfileDataLoader.readCsvRecord(reader)).isEqualTo("2,y,z");
        }
    }

    @Test
    void deterministicProfileId_isStableAndUniqueByRowKey() {
        assertThat(OkCupidProfileDataLoader.deterministicId("row-10"))
                .isEqualTo(OkCupidProfileDataLoader.deterministicId("row-10"));
        assertThat(OkCupidProfileDataLoader.deterministicId("row-10"))
                .isNotEqualTo(OkCupidProfileDataLoader.deterministicId("row-11"));
    }

    @Test
    void parserRetainsCommaDelimitedCityAndIncomeSentinelCanBeNormalized() {
        var fields = OkCupidProfileDataLoader.parseCsvLine("\"san francisco, california\",-1,\"about, text\"");
        assertThat(fields).containsExactly("san francisco, california", "-1", "about, text");
        assertThat(OkCupidProfileDataLoader.parseIncome(fields.get(1))).isNull();
        assertThat(OkCupidProfileDataLoader.parseLocation(fields.get(0)))
                .isEqualTo(new OkCupidProfileDataLoader.LocationParts("United States", "San Francisco"));
    }

    @Test
    void parsesCsvSexOrientationToEnums() {
        assertThat(OkCupidProfileDataLoader.parseGender("m")).isEqualTo(Gender.MALE);
        assertThat(OkCupidProfileDataLoader.parseGender("f")).isEqualTo(Gender.FEMALE);
        assertThat(OkCupidProfileDataLoader.parseOrientation("bisexual")).isEqualTo(Orientation.BISEXUAL);
    }

    @Test
    void effectiveLimit_defaultsToConfiguredDefaultAndCapsAtDatasetSize() {
        assertThat(OkCupidProfileDataLoader.effectiveLimit(1_000)).isEqualTo(1_000);
        assertThat(OkCupidProfileDataLoader.effectiveLimit(20_000)).isEqualTo(10_000);
    }

    @Test
    void effectiveLimit_rejectsZeroOrNegativeValues() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> OkCupidProfileDataLoader.effectiveLimit(0))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> OkCupidProfileDataLoader.effectiveLimit(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
