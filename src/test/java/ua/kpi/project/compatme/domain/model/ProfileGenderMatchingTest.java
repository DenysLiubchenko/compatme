package ua.kpi.project.compatme.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link Profile#matchesSeekingGender(Profile)} and
 * {@link Profile#mutuallyMatchesSeekingGender(Profile)}, in particular that a multi-value
 * {@code seekingGenders} set is treated as "seeking any of these genders" rather than requiring
 * an exact single-value match.
 */
class ProfileGenderMatchingTest {

    @Test
    void mutuallyMatches_whenBothSidesSeekASingleGenderAndItMatches() {
        // GIVEN
        Profile male = profile(Gender.MALE, Set.of(Gender.FEMALE));
        Profile female = profile(Gender.FEMALE, Set.of(Gender.MALE));

        // WHEN / THEN
        assertThat(male.mutuallyMatchesSeekingGender(female)).isTrue();
    }

    @Test
    void doesNotMutuallyMatch_whenSingleGenderSeekingDoesNotAlign() {
        // GIVEN
        Profile male = profile(Gender.MALE, Set.of(Gender.FEMALE));
        Profile otherMale = profile(Gender.MALE, Set.of(Gender.FEMALE));

        // WHEN / THEN
        assertThat(male.mutuallyMatchesSeekingGender(otherMale)).isFalse();
    }

    @Test
    void mutuallyMatches_whenOneSideSeeksMultipleGendersAndTheOtherSideIsIncluded() {
        // GIVEN: requester is MALE and seeks both MALE and FEMALE; candidate is FEMALE and only seeks MALE.
        Profile requester = profile(Gender.MALE, Set.of(Gender.MALE, Gender.FEMALE));
        Profile candidate = profile(Gender.FEMALE, Set.of(Gender.MALE));

        // WHEN / THEN
        assertThat(requester.mutuallyMatchesSeekingGender(candidate)).isTrue();
    }

    @Test
    void doesNotMutuallyMatch_whenOneSideSeeksMultipleGendersButCandidateDoesNotSeekRequesterGender() {
        // GIVEN: requester seeks both MALE and FEMALE; candidate is FEMALE but only seeks FEMALE.
        Profile requester = profile(Gender.OTHER, Set.of(Gender.MALE, Gender.FEMALE));
        Profile candidate = profile(Gender.FEMALE, Set.of(Gender.FEMALE));

        // WHEN / THEN
        assertThat(requester.mutuallyMatchesSeekingGender(candidate)).isFalse();
    }

    @Test
    void mutuallyMatches_whenBothSidesSeekMultipleGenders() {
        // GIVEN: both sides are open to either gender.
        Profile a = profile(Gender.MALE, Set.of(Gender.MALE, Gender.FEMALE));
        Profile b = profile(Gender.FEMALE, Set.of(Gender.MALE, Gender.FEMALE));

        // WHEN / THEN
        assertThat(a.mutuallyMatchesSeekingGender(b)).isTrue();
    }

    @Test
    void doesNotMutuallyMatch_whenBothSidesSeekMultipleGendersButNeitherGenderIsIncluded() {
        // GIVEN: both seek exactly {MALE, FEMALE}, but both declare gender OTHER, which neither is seeking.
        Profile a = profile(Gender.OTHER, Set.of(Gender.MALE, Gender.FEMALE));
        Profile b = profile(Gender.OTHER, Set.of(Gender.MALE, Gender.FEMALE));

        // WHEN / THEN
        assertThat(a.mutuallyMatchesSeekingGender(b)).isFalse();
    }

    private static Profile profile(Gender gender, Set<Gender> seekingGenders) {
        Instant now = Instant.now();
        return new Profile(
                ProfileId.generate(), null, "name", 25, gender, seekingGenders,
                "self description", "preference description", ProfileEmbeddings.empty(), now, now);
    }
}
