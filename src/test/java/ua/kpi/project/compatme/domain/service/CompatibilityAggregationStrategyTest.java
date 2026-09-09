package ua.kpi.project.compatme.domain.service;

import org.junit.jupiter.api.Test;
import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.model.DirectionalScores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the reciprocal-matching aggregation strategies — the core contribution of the
 * thesis. Verified with zero Spring context, per the hexagonal architecture requirement that
 * this logic be testable in complete isolation.
 */
class CompatibilityAggregationStrategyTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void simpleAverage_returnsArithmeticMeanOfDirectionalScores() {
        // GIVEN
        SimpleAverageAggregationStrategy strategy = new SimpleAverageAggregationStrategy();
        DirectionalScores scores = new DirectionalScores(0.8, 0.4, 0.5);

        // WHEN
        double result = strategy.aggregate(scores);

        // THEN
        assertThat(result).isCloseTo(0.6, within(TOLERANCE));
        assertThat(strategy.type()).isEqualTo(AggregationStrategyType.SIMPLE_AVERAGE);
    }

    @Test
    void simpleSelfSimilarity_returnsSelfSelfScoreIgnoringDirectionalScores() {
        // GIVEN
        SimpleSelfSimilarityAggregationStrategy strategy = new SimpleSelfSimilarityAggregationStrategy();
        DirectionalScores scores = new DirectionalScores(0.1, 0.9, 0.73);

        // WHEN
        double result = strategy.aggregate(scores);

        // THEN
        assertThat(result).isCloseTo(0.73, within(TOLERANCE));
        assertThat(strategy.type()).isEqualTo(AggregationStrategyType.SIMPLE_SELF_SIMILARITY);
    }

    @Test
    void reciprocalHarmonic_equalsSimpleAverage_whenBothDirectionalScoresAreEqual() {
        // GIVEN: harmonic mean of two equal values equals their arithmetic mean
        ReciprocalHarmonicAggregationStrategy strategy = new ReciprocalHarmonicAggregationStrategy();
        DirectionalScores scores = new DirectionalScores(0.6, 0.6, 0.0);

        // WHEN
        double result = strategy.aggregate(scores);

        // THEN
        assertThat(result).isCloseTo(0.6, within(TOLERANCE));
    }

    @Test
    void reciprocalHarmonic_penalizesOneSidedInterestMoreThanSimpleAverage() {
        // GIVEN: A is very interested in B (0.95), but B is barely interested in A (0.05)
        ReciprocalHarmonicAggregationStrategy reciprocal = new ReciprocalHarmonicAggregationStrategy();
        SimpleAverageAggregationStrategy average = new SimpleAverageAggregationStrategy();
        DirectionalScores oneSidedScores = new DirectionalScores(0.95, 0.05, 0.0);

        // WHEN
        double reciprocalResult = reciprocal.aggregate(oneSidedScores);
        double averageResult = average.aggregate(oneSidedScores);

        // THEN: the harmonic mean must be pulled much closer to the smaller (weaker) score
        assertThat(reciprocalResult).isLessThan(averageResult);
        assertThat(reciprocalResult).isCloseTo(2.0 * 0.95 * 0.05 / (0.95 + 0.05), within(TOLERANCE));
    }

    @Test
    void reciprocalHarmonic_returnsZero_whenEitherDirectionalScoreIsZeroOrNegative() {
        // GIVEN
        ReciprocalHarmonicAggregationStrategy strategy = new ReciprocalHarmonicAggregationStrategy();

        // WHEN / THEN
        assertThat(strategy.aggregate(new DirectionalScores(0.0, 0.8, 0.0))).isEqualTo(0.0);
        assertThat(strategy.aggregate(new DirectionalScores(-0.3, 0.8, 0.0))).isEqualTo(0.0);
        assertThat(strategy.aggregate(new DirectionalScores(0.5, -0.1, 0.0))).isEqualTo(0.0);
        assertThat(strategy.type()).isEqualTo(AggregationStrategyType.RECIPROCAL_HARMONIC);
    }
}
