package ua.kpi.project.compatme.domain.service;

import ua.kpi.project.compatme.domain.model.DirectionalScores;

/**
 * The project's single compatibility-scoring method: harmonic mean of the two directional scores.
 *
 * <pre>reciprocalScore = 2 * scoreAtoB * scoreBtoA / (scoreAtoB + scoreBtoA)</pre>
 *
 * <p>This is the thesis's core contribution and the sole aggregation mode the app uses. Unlike a
 * plain average, the harmonic mean collapses toward zero whenever either direction is weak, so a
 * recommendation is only ranked highly when <em>both</em> sides are plausibly interested in each
 * other — one-directional infatuation does not produce a high score. A recommendation is only
 * genuinely valuable if the interest is mutual; a simple average would let one very high
 * directional score mask a very low one, recommending people who are unlikely to reciprocate.
 *
 * <p>Cosine similarity can be negative for unrelated/opposed text; the harmonic mean is only
 * mathematically well-behaved for same-signed positive inputs, so negative or zero-sum inputs are
 * clamped to {@code 0.0} rather than producing a nonsensical (e.g. negative-of-negative) result.
 */
public final class ReciprocalHarmonicAggregationStrategy implements CompatibilityAggregationStrategy {

    @Override
    public double aggregate(DirectionalScores scores) {
        double a = scores.scoreAtoB();
        double b = scores.scoreBtoA();
        double sum = a + b;
        if (a <= 0.0 || b <= 0.0 || sum <= 0.0) {
            return 0.0;
        }
        return 2.0 * a * b / sum;
    }
}
