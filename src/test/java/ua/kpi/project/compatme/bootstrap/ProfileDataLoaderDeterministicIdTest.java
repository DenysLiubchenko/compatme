package ua.kpi.project.compatme.bootstrap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks in the deterministic-id guarantee {@link ProfileDataLoader} relies on for idempotent
 * re-seeding: the same {@code sampleKey} must always hash to the same id (so re-running the
 * loader updates rather than duplicates), and different keys must not collide.
 */
class ProfileDataLoaderDeterministicIdTest {

    @Test
    void deterministicProfileId_isStableForTheSameSampleKey() {
        String first = ProfileDataLoader.deterministicProfileId("seed-001");
        String second = ProfileDataLoader.deterministicProfileId("seed-001");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void deterministicProfileId_differsAcrossDifferentSampleKeys() {
        String seed1 = ProfileDataLoader.deterministicProfileId("seed-001");
        String seed2 = ProfileDataLoader.deterministicProfileId("seed-002");

        assertThat(seed1).isNotEqualTo(seed2);
    }

    @Test
    void deterministicProfileId_matchesTheDocumentedAlgorithm() {
        // Regression guard: if this ever changes, every previously-published ground-truth.json
        // referencing these ids silently breaks. Pin the exact expected value.
        assertThat(ProfileDataLoader.deterministicProfileId("seed-001"))
                .isEqualTo("dd3112a1-ecfd-331d-8bb4-f7257606d6ce");
    }
}
