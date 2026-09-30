package ua.kpi.project.compatme.domain.service;

import ua.kpi.project.compatme.domain.model.CandidateMatch;
import ua.kpi.project.compatme.domain.model.DirectionalScores;
import ua.kpi.project.compatme.domain.model.EmbeddingVector;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileEmbeddings;

import java.util.Objects;

/**
 * Domain service that computes the directional compatibility scores between two profiles and
 * aggregates them via the single {@link CompatibilityAggregationStrategy} (reciprocal harmonic
 * mean). This is the single place where the "directional compatibility score" formulas from the
 * thesis specification are implemented:
 *
 * <pre>
 * scoreAtoB = cosineSimilarity(embedding(preferenceDescription_A), embedding(selfDescription_B))
 * scoreBtoA = cosineSimilarity(embedding(preferenceDescription_B), embedding(selfDescription_A))
 * </pre>
 *
 * <p>Zero Spring/MongoDB/Gemini dependencies — operates purely on already-computed
 * {@link EmbeddingVector}s, so it is fully unit-testable in isolation.
 */
public final class CompatibilityScorer {

    private final CompatibilityAggregationStrategy aggregationStrategy;

    public CompatibilityScorer(CompatibilityAggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = Objects.requireNonNull(aggregationStrategy, "aggregationStrategy must not be null");
    }

    /**
     * Scores {@code candidate} against {@code requester}.
     *
     * @throws IllegalStateException if either profile is missing a required embedding
     */
    public CandidateMatch score(Profile requester, Profile candidate) {
        DirectionalScores directionalScores = computeDirectionalScores(requester, candidate);
        double aggregated = aggregationStrategy.aggregate(directionalScores);
        return new CandidateMatch(candidate.id(), directionalScores, aggregated);
    }

    private DirectionalScores computeDirectionalScores(Profile requester, Profile candidate) {
        ProfileEmbeddings requesterEmbeddings = requireCompleteEmbeddings(requester);
        ProfileEmbeddings candidateEmbeddings = requireCompleteEmbeddings(candidate);

        double scoreAtoB = CosineSimilarity.compute(
                requesterEmbeddings.preferenceEmbedding().values(),
                candidateEmbeddings.selfEmbedding().values());
        double scoreBtoA = CosineSimilarity.compute(
                candidateEmbeddings.preferenceEmbedding().values(),
                requesterEmbeddings.selfEmbedding().values());
        double selfSelfSimilarity = CosineSimilarity.compute(
                requesterEmbeddings.selfEmbedding().values(),
                candidateEmbeddings.selfEmbedding().values());

        return new DirectionalScores(scoreAtoB, scoreBtoA, selfSelfSimilarity);
    }

    private ProfileEmbeddings requireCompleteEmbeddings(Profile profile) {
        ProfileEmbeddings embeddings = profile.embeddings();
        if (!embeddings.isComplete()) {
            throw new IllegalStateException(
                    "Profile " + profile.id() + " is missing required embeddings; "
                            + "generate embeddings before scoring");
        }
        return embeddings;
    }
}
