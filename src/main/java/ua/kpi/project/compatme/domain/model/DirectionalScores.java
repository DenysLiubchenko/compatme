package ua.kpi.project.compatme.domain.model;

/**
 * The raw cosine-similarity inputs that every {@code CompatibilityAggregationStrategy} draws
 * from, computed once per candidate pair and shared across strategies.
 *
 * @param scoreAtoB cosine(preference_A, self_B) — how well B matches what A is looking for
 * @param scoreBtoA cosine(preference_B, self_A) — how well A matches what B is looking for
 * @param selfSelfSimilarity cosine(self_A, self_B) — symmetric similarity of the two "who I am" texts
 */
public record DirectionalScores(double scoreAtoB, double scoreBtoA, double selfSelfSimilarity) {
}
