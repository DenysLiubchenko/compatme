package ua.kpi.project.compatme.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DealBreakersTest {

    @Test
    void rejectsKnownConfiguredCandidateAttributeButAllowsUnknownValue() {
        DealBreakers dealBreakers = new DealBreakers(
                Set.of(), Set.of(SmokingStatus.YES), Set.of(), Set.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(dealBreakers.rejects(profile(SmokingStatus.YES))).isTrue();
        assertThat(dealBreakers.rejects(profile(null))).isFalse();
    }

    private static Profile profile(SmokingStatus smokes) {
        Instant now = Instant.now();
        return Profile.builder().id(ProfileId.generate()).displayName("Candidate").age(30)
                .gender(Gender.MALE).orientation(Orientation.STRAIGHT).country("Ukraine").city("Kyiv")
                .selfDescription("About me").preferenceDescription("About you")
                .smokes(smokes).createdAt(now).updatedAt(now).build();
    }
}
