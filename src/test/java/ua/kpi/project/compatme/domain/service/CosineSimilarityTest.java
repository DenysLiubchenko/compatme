package ua.kpi.project.compatme.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CosineSimilarityTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void identicalVectors_haveSimilarityOfOne() {
        float[] a = {1f, 2f, 3f};
        float[] b = {1f, 2f, 3f};

        assertThat(CosineSimilarity.compute(a, b)).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void orthogonalVectors_haveSimilarityOfZero() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};

        assertThat(CosineSimilarity.compute(a, b)).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void oppositeVectors_haveSimilarityOfNegativeOne() {
        float[] a = {1f, 0f};
        float[] b = {-1f, 0f};

        assertThat(CosineSimilarity.compute(a, b)).isCloseTo(-1.0, within(TOLERANCE));
    }

    @Test
    void zeroMagnitudeVector_returnsZeroInsteadOfDividingByZero() {
        float[] zero = {0f, 0f};
        float[] other = {1f, 1f};

        assertThat(CosineSimilarity.compute(zero, other)).isEqualTo(0.0);
    }

    @Test
    void mismatchedDimensions_throwIllegalArgumentException() {
        float[] a = {1f, 2f};
        float[] b = {1f, 2f, 3f};

        assertThatThrownBy(() -> CosineSimilarity.compute(a, b))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
