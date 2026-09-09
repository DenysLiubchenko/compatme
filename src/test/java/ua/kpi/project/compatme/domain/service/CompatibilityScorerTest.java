package ua.kpi.project.compatme.domain.service;

import org.junit.jupiter.api.Test;
import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.model.CandidateMatch;
import ua.kpi.project.compatme.domain.model.EmbeddingVector;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileEmbeddings;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompatibilityScorerTest {

    private static final String MODEL = "gemini-embedding-001";

    private final CompatibilityScorer scorer = new CompatibilityScorer(Map.of(
            AggregationStrategyType.SIMPLE_AVERAGE, new SimpleAverageAggregationStrategy(),
            AggregationStrategyType.SIMPLE_SELF_SIMILARITY, new SimpleSelfSimilarityAggregationStrategy(),
            AggregationStrategyType.RECIPROCAL_HARMONIC, new ReciprocalHarmonicAggregationStrategy()));

    @Test
    void score_computesDirectionalScoresAndAppliesSelectedStrategy() {
        // GIVEN: A's preference vector matches B's self vector well ([1,0]),
        // and B's preference vector matches A's self vector well too ([0,1] vs [0,1])
        Profile a = profileWith("Марія", embedding(new float[]{0f, 1f}), embedding(new float[]{1f, 0f}));
        Profile b = profileWith("Олена", embedding(new float[]{1f, 0f}), embedding(new float[]{0f, 1f}));

        // WHEN
        CandidateMatch match = scorer.score(a, b, AggregationStrategyType.RECIPROCAL_HARMONIC);

        // THEN
        assertThat(match.candidateId()).isEqualTo(b.id());
        assertThat(match.directionalScores().scoreAtoB()).isEqualTo(1.0);
        assertThat(match.directionalScores().scoreBtoA()).isEqualTo(1.0);
        assertThat(match.aggregatedScore()).isEqualTo(1.0);
    }

    @Test
    void score_throwsIllegalStateException_whenEmbeddingsAreMissing() {
        // GIVEN
        Profile withoutEmbeddings = profileWithoutEmbeddings("Тарас");
        Profile withEmbeddings = profileWith("Олена", embedding(new float[]{1f, 0f}), embedding(new float[]{0f, 1f}));

        // WHEN / THEN
        assertThatThrownBy(() -> scorer.score(withoutEmbeddings, withEmbeddings, AggregationStrategyType.SIMPLE_AVERAGE))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Profile profileWith(String name, EmbeddingVector selfEmbedding, EmbeddingVector prefEmbedding) {
        Instant now = Instant.now();
        return new Profile(
                ProfileId.generate(), null, name, 25, Gender.OTHER, Set.of(),
                "self description", "preference description",
                new ProfileEmbeddings(selfEmbedding, prefEmbedding), now, now);
    }

    private static Profile profileWithoutEmbeddings(String name) {
        Instant now = Instant.now();
        return new Profile(
                ProfileId.generate(), null, name, 25, Gender.OTHER, Set.of(),
                "self description", "preference description",
                ProfileEmbeddings.empty(), now, now);
    }

    private static EmbeddingVector embedding(float[] values) {
        return new EmbeddingVector(values, MODEL, values.length, "hash", Instant.now());
    }
}
