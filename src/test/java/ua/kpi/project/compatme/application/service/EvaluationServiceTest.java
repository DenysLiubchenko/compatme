package ua.kpi.project.compatme.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.kpi.project.compatme.application.dto.EvaluationReport;
import ua.kpi.project.compatme.application.port.out.GroundTruthPairRepositoryPort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.model.EmbeddingVector;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.GroundTruthLabel;
import ua.kpi.project.compatme.domain.model.GroundTruthPair;
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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link EvaluationService}'s grouping/averaging logic using hand-crafted embeddings
 * (chosen so each pair's aggregated score under each strategy is a known, simple value) and the
 * real {@link CompatibilityScorer} — no mocking of the scorer, matching the style of
 * {@link RecommendationServiceTest}. Only {@link ProfileRepositoryPort} and
 * {@link GroundTruthPairRepositoryPort} are mocked.
 */
@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    private static final double TOLERANCE = 1e-9;

    @Mock
    private GroundTruthPairRepositoryPort groundTruthPairRepository;

    @Mock
    private ProfileRepositoryPort profileRepository;

    private final CompatibilityScorer scorer = new CompatibilityScorer(Map.of(
            AggregationStrategyType.SIMPLE_AVERAGE, new SimpleAverageAggregationStrategy(),
            AggregationStrategyType.SIMPLE_SELF_SIMILARITY, new SimpleSelfSimilarityAggregationStrategy(),
            AggregationStrategyType.RECIPROCAL_HARMONIC, new ReciprocalHarmonicAggregationStrategy()));

    @Test
    void generateReport_computesAveragePerLabelAndStrategyAcrossMultiplePairs() {
        // GIVEN two MUTUAL_MATCH pairs with different scores, and one NO_MATCH pair, all scorable.
        Profile mm1a = profileWith("mm1a", embedding(1f, 0f), embedding(1f, 0f));
        Profile mm1b = profileWith("mm1b", embedding(1f, 0f), embedding(1f, 0f));
        // scoreAtoB=cos(pref_a=[1,0], self_b=[1,0])=1; scoreBtoA=cos(pref_b=[1,0], self_a=[1,0])=1
        // -> SIMPLE_AVERAGE=1.0, SELF_SELF_SIMILARITY=1.0, RECIPROCAL_HARMONIC=1.0

        Profile mm2a = profileWith("mm2a", embedding(1f, 0f), embedding(1f, 0f));
        Profile mm2b = profileWith("mm2b", embedding(1f, 0f), embedding(0f, 1f));
        // scoreAtoB=cos(pref_a=[1,0], self_b=[1,0])=1; scoreBtoA=cos(pref_b=[0,1], self_a=[1,0])=0
        // -> SIMPLE_AVERAGE=0.5, SELF_SELF_SIMILARITY=cos([1,0],[1,0])=1.0, RECIPROCAL_HARMONIC=0.0 (b<=0)

        Profile nma = profileWith("nma", embedding(0f, 1f), embedding(0f, 1f));
        Profile nmb = profileWith("nmb", embedding(1f, 0f), embedding(1f, 0f));
        // scoreAtoB=cos(pref_a=[0,1], self_b=[1,0])=0; scoreBtoA=cos(pref_b=[1,0], self_a=[0,1])=0
        // -> all strategies = 0.0

        GroundTruthPair pairMm1 = new GroundTruthPair(mm1a.id(), mm1b.id(), GroundTruthLabel.MUTUAL_MATCH);
        GroundTruthPair pairMm2 = new GroundTruthPair(mm2a.id(), mm2b.id(), GroundTruthLabel.MUTUAL_MATCH);
        GroundTruthPair pairNm = new GroundTruthPair(nma.id(), nmb.id(), GroundTruthLabel.NO_MATCH);

        when(groundTruthPairRepository.findAll()).thenReturn(List.of(pairMm1, pairMm2, pairNm));
        when(profileRepository.findById(mm1a.id())).thenReturn(Optional.of(mm1a));
        when(profileRepository.findById(mm1b.id())).thenReturn(Optional.of(mm1b));
        when(profileRepository.findById(mm2a.id())).thenReturn(Optional.of(mm2a));
        when(profileRepository.findById(mm2b.id())).thenReturn(Optional.of(mm2b));
        when(profileRepository.findById(nma.id())).thenReturn(Optional.of(nma));
        when(profileRepository.findById(nmb.id())).thenReturn(Optional.of(nmb));

        EvaluationService service = new EvaluationService(groundTruthPairRepository, profileRepository, scorer);

        // WHEN
        EvaluationReport report = service.generateReport();

        // THEN
        assertThat(report.pairCounts().get(GroundTruthLabel.MUTUAL_MATCH)).isEqualTo(2);
        assertThat(report.pairCounts().get(GroundTruthLabel.NO_MATCH)).isEqualTo(1);
        assertThat(report.pairCounts().get(GroundTruthLabel.ONE_SIDED)).isEqualTo(0);

        Map<AggregationStrategyType, Double> mutualMatchAverages =
                report.resultsByLabelAndStrategy().get(GroundTruthLabel.MUTUAL_MATCH);
        assertThat(mutualMatchAverages.get(AggregationStrategyType.SIMPLE_AVERAGE)).isCloseTo(0.75, within(TOLERANCE));
        assertThat(mutualMatchAverages.get(AggregationStrategyType.SIMPLE_SELF_SIMILARITY)).isCloseTo(1.0, within(TOLERANCE));
        assertThat(mutualMatchAverages.get(AggregationStrategyType.RECIPROCAL_HARMONIC)).isCloseTo(0.5, within(TOLERANCE));

        Map<AggregationStrategyType, Double> noMatchAverages =
                report.resultsByLabelAndStrategy().get(GroundTruthLabel.NO_MATCH);
        assertThat(noMatchAverages.get(AggregationStrategyType.SIMPLE_AVERAGE)).isCloseTo(0.0, within(TOLERANCE));
        assertThat(noMatchAverages.get(AggregationStrategyType.SIMPLE_SELF_SIMILARITY)).isCloseTo(0.0, within(TOLERANCE));
        assertThat(noMatchAverages.get(AggregationStrategyType.RECIPROCAL_HARMONIC)).isCloseTo(0.0, within(TOLERANCE));

        // ONE_SIDED has no pairs -> averages default to 0.0 rather than throwing/NaN.
        Map<AggregationStrategyType, Double> oneSidedAverages =
                report.resultsByLabelAndStrategy().get(GroundTruthLabel.ONE_SIDED);
        assertThat(oneSidedAverages.values()).allSatisfy(value -> assertThat(value).isCloseTo(0.0, within(TOLERANCE)));
    }

    @Test
    void generateReport_skipsPairsWithMissingProfilesOrIncompleteEmbeddings() {
        // GIVEN one pair referencing a profile that doesn't exist, and one with incomplete embeddings.
        Profile complete = profileWith("complete", embedding(1f, 0f), embedding(1f, 0f));
        Profile incomplete = profileWithoutEmbeddings("incomplete");
        ProfileId missingId = ProfileId.generate();

        GroundTruthPair missingProfilePair = new GroundTruthPair(complete.id(), missingId, GroundTruthLabel.MUTUAL_MATCH);
        GroundTruthPair incompleteEmbeddingsPair =
                new GroundTruthPair(complete.id(), incomplete.id(), GroundTruthLabel.MUTUAL_MATCH);

        when(groundTruthPairRepository.findAll()).thenReturn(List.of(missingProfilePair, incompleteEmbeddingsPair));
        when(profileRepository.findById(complete.id())).thenReturn(Optional.of(complete));
        when(profileRepository.findById(missingId)).thenReturn(Optional.empty());
        when(profileRepository.findById(incomplete.id())).thenReturn(Optional.of(incomplete));

        EvaluationService service = new EvaluationService(groundTruthPairRepository, profileRepository, scorer);

        // WHEN
        EvaluationReport report = service.generateReport();

        // THEN both pairs were skipped -> zero contributing pairs for every label.
        assertThat(report.pairCounts().values()).allSatisfy(count -> assertThat(count).isZero());
    }

    private static Profile profileWith(String name, EmbeddingVector selfEmbedding, EmbeddingVector preferenceEmbedding) {
        Instant now = Instant.now();
        return new Profile(
                ProfileId.generate(), null, name, 25, Gender.OTHER, Set.of(),
                "self description", "preference description",
                new ProfileEmbeddings(selfEmbedding, preferenceEmbedding), now, now);
    }

    private static Profile profileWithoutEmbeddings(String name) {
        Instant now = Instant.now();
        return new Profile(
                ProfileId.generate(), null, name, 25, Gender.OTHER, Set.of(),
                "self description", "preference description", ProfileEmbeddings.empty(), now, now);
    }

    private static EmbeddingVector embedding(float... values) {
        return new EmbeddingVector(values, "gemini-embedding-001", values.length, "hash", Instant.now());
    }
}
