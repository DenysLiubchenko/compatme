package ua.kpi.project.compatme.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.kpi.project.compatme.application.dto.GetRecommendationsQuery;
import ua.kpi.project.compatme.application.dto.RecommendationResult;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.model.EmbeddingVector;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileEmbeddings;
import ua.kpi.project.compatme.domain.model.ProfileId;
import ua.kpi.project.compatme.domain.service.CompatibilityScorer;
import ua.kpi.project.compatme.domain.service.ReciprocalHarmonicAggregationStrategy;
import ua.kpi.project.compatme.domain.service.SimpleAverageAggregationStrategy;
import ua.kpi.project.compatme.domain.service.SimpleSelfSimilarityAggregationStrategy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private ProfileRepositoryPort profileRepository;

    private final CompatibilityScorer scorer = new CompatibilityScorer(Map.of(
            AggregationStrategyType.SIMPLE_AVERAGE, new SimpleAverageAggregationStrategy(),
            AggregationStrategyType.SIMPLE_SELF_SIMILARITY, new SimpleSelfSimilarityAggregationStrategy(),
            AggregationStrategyType.RECIPROCAL_HARMONIC, new ReciprocalHarmonicAggregationStrategy()));

    @Test
    void recommend_excludesCandidatesWithoutEmbeddingsAndSortsDescendingByScore() {
        // GIVEN
        RecommendationService service = new RecommendationService(profileRepository, scorer);
        Profile requester = profileWith("requester", 28, embedding(new float[]{1f, 0f}), embedding(new float[]{0f, 1f}));
        Profile strongMatch = profileWith("strong", 30, embedding(new float[]{0f, 1f}), embedding(new float[]{1f, 0f}));
        Profile weakMatch = profileWith("weak", 26, embedding(new float[]{1f, 1f}), embedding(new float[]{1f, 1f}));
        Profile noEmbeddings = profileWithoutEmbeddings("no-embeddings", 27);

        when(profileRepository.findById(requester.id())).thenReturn(Optional.of(requester));
        when(profileRepository.findCandidates(any(), any()))
                .thenReturn(List.of(weakMatch, strongMatch, noEmbeddings));

        // WHEN
        List<RecommendationResult> results = service.recommend(
                new GetRecommendationsQuery(requester.id().value(), AggregationStrategyType.RECIPROCAL_HARMONIC, 10));

        // THEN
        assertThat(results).hasSize(2);
        assertThat(results.get(0).candidateProfile().id()).isEqualTo(strongMatch.id());
        assertThat(results.get(0).match().aggregatedScore())
                .isGreaterThan(results.get(1).match().aggregatedScore());
    }

    @Test
    void recommend_appliesTopNLimit() {
        // GIVEN
        RecommendationService service = new RecommendationService(profileRepository, scorer);
        Profile requester = profileWith("requester", 28, embedding(new float[]{1f, 0f}), embedding(new float[]{0f, 1f}));
        List<Profile> manyCandidates = List.of(
                profileWith("a", 28, embedding(new float[]{0f, 1f}), embedding(new float[]{1f, 0f})),
                profileWith("b", 28, embedding(new float[]{0f, 1f}), embedding(new float[]{1f, 0f})),
                profileWith("c", 28, embedding(new float[]{0f, 1f}), embedding(new float[]{1f, 0f})));

        when(profileRepository.findById(requester.id())).thenReturn(Optional.of(requester));
        when(profileRepository.findCandidates(any(), any())).thenReturn(manyCandidates);

        // WHEN
        List<RecommendationResult> results = service.recommend(
                new GetRecommendationsQuery(requester.id().value(), AggregationStrategyType.SIMPLE_AVERAGE, 2));

        // THEN
        assertThat(results).hasSize(2);
    }

    private static Profile profileWith(String name, int age, EmbeddingVector selfEmbedding, EmbeddingVector prefEmbedding) {
        Instant now = Instant.now();
        return new Profile(
                ProfileId.generate(), null, name, age, Gender.OTHER, Set.of(),
                "self description", "preference description",
                new ProfileEmbeddings(selfEmbedding, prefEmbedding), now, now);
    }

    private static Profile profileWithoutEmbeddings(String name, int age) {
        Instant now = Instant.now();
        return new Profile(
                ProfileId.generate(), null, name, age, Gender.OTHER, Set.of(),
                "self description", "preference description",
                ProfileEmbeddings.empty(), now, now);
    }

    private static EmbeddingVector embedding(float[] values) {
        return new EmbeddingVector(values, "gemini-embedding-001", values.length, "hash", Instant.now());
    }
}
