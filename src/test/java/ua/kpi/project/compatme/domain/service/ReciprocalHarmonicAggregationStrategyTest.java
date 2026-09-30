package ua.kpi.project.compatme.domain.service;

import org.junit.jupiter.api.Test;
import ua.kpi.project.compatme.domain.model.DirectionalScores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the reciprocal harmonic-mean aggregation formula — the thesis's core
 * contribution and the app's single compatibility-scoring method. Verified with zero Spring
 * context, per the hexagonal architecture requirement that this logic be testable in complete
 * isolation.
 */
class ReciprocalHarmonicAggregationStrategyTest {

    private static final double TOLERANCE = 1e-9;

    private final ReciprocalHarmonicAggregationStrategy strategy = new ReciprocalHarmonicAggregationStrategy();

    @Test
    void equalsArithmeticMean_whenBothDirectionalScoresAreEqual() {
        // GIVEN: harmonic mean of two equal values equals their arithmetic mean
        DirectionalScores scores = new DirectionalScores(0.6, 0.6, 0.0);

        // WHEN
        double result = strategy.aggregate(scores);

        // THEN
        assertThat(result).isCloseTo(0.6, within(TOLERANCE));
    }

    @Test
    void penalizesOneSidedInterest_moreThanASimpleAverageWould() {
        // GIVEN: A is very interested in B (0.95), but B is barely interested in A (0.05).
        // This is the thesis's core justification for reciprocal harmonic aggregation over a
        // plain average: a recommendation is only valuable if interest is mutual, so one-sided
        // infatuation must be penalized more heavily than a simple average would penalize it.
        DirectionalScores oneSidedScores = new DirectionalScores(0.95, 0.05, 0.0);
        double simpleAverage = (0.95 + 0.05) / 2.0; // = 0.5, for comparison only

        // WHEN
        double result = strategy.aggregate(oneSidedScores);

        // THEN: the harmonic mean is pulled much closer to the smaller (weaker) score
        assertThat(result).isLessThan(simpleAverage);
        assertThat(result).isCloseTo(2.0 * 0.95 * 0.05 / (0.95 + 0.05), within(TOLERANCE));
        assertThat(result).isCloseTo(0.095, within(1e-3));
    }

    @Test
    void returnsZero_whenEitherDirectionalScoreIsZeroOrNegative() {
        // Cosine similarity can be negative for unrelated/opposed text; the harmonic mean is only
        // well-behaved for same-signed positive inputs, so these are clamped to 0.0.
        assertThat(strategy.aggregate(new DirectionalScores(0.0, 0.8, 0.0))).isEqualTo(0.0);
        assertThat(strategy.aggregate(new DirectionalScores(-0.3, 0.8, 0.0))).isEqualTo(0.0);
        assertThat(strategy.aggregate(new DirectionalScores(0.5, -0.1, 0.0))).isEqualTo(0.0);
        assertThat(strategy.aggregate(new DirectionalScores(0.5, 0.0, 0.0))).isEqualTo(0.0);
    }

    @Test
    void ignoresSelfSelfSimilarity_onlyDirectionalScoresMatter() {
        DirectionalScores lowSelfSelf = new DirectionalScores(0.7, 0.7, 0.01);
        DirectionalScores highSelfSelf = new DirectionalScores(0.7, 0.7, 0.99);

        assertThat(strategy.aggregate(lowSelfSelf)).isCloseTo(strategy.aggregate(highSelfSelf), within(TOLERANCE));
    }
}
