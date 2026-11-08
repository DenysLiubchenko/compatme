package ua.kpi.project.compatme.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.kpi.project.compatme.application.dto.EmbeddingGenerationResult;
import ua.kpi.project.compatme.application.dto.GetRecommendationsQuery;
import ua.kpi.project.compatme.application.dto.RecommendationResult;
import ua.kpi.project.compatme.application.dto.RefinePreferenceCommand;
import ua.kpi.project.compatme.application.dto.RefinePreferenceResult;
import ua.kpi.project.compatme.application.exception.ChatCompletionException;
import ua.kpi.project.compatme.application.exception.EmbeddingGenerationException;
import ua.kpi.project.compatme.application.port.in.GenerateEmbeddingsUseCase;
import ua.kpi.project.compatme.application.port.in.RecommendationUseCase;
import ua.kpi.project.compatme.application.port.out.ChatCompletionPort;
import ua.kpi.project.compatme.application.port.out.EmbeddingProviderPort;
import ua.kpi.project.compatme.application.port.out.PreferenceUpdateLogPort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.EmbeddingVector;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Orientation;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileEmbeddings;
import ua.kpi.project.compatme.domain.model.ProfileId;
import ua.kpi.project.compatme.domain.service.CompatibilityScorer;
import ua.kpi.project.compatme.domain.service.ReciprocalHarmonicAggregationStrategy;
import ua.kpi.project.compatme.domain.service.TextHasher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The application must keep working when the Gemini chat/embedding provider is unavailable. */
@ExtendWith(MockitoExtension.class)
class GeminiOutageDegradationTest {

    private static final String MODEL = "test-embedding";

    @Mock private ProfileRepositoryPort profiles;
    @Mock private EmbeddingProviderPort embeddings;
    @Mock private ChatCompletionPort chat;
    @Mock private PreferenceUpdateLogPort updateLog;
    @Mock private RecommendationUseCase recommendations;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(embeddings.modelName()).thenReturn(MODEL);
    }

    // ---- embedding generation ----------------------------------------------------------------

    @Test
    void generateEmbeddings_whenProviderDown_doesNotFail_andReportsPending() {
        Profile profile = profile("1001", ProfileEmbeddings.empty());
        when(profiles.findById(profile.id())).thenReturn(Optional.of(profile));
        when(embeddings.embed(anyString())).thenThrow(new EmbeddingGenerationException("down", null));

        EmbeddingGenerationResult result = new EmbeddingGenerationService(profiles, embeddings)
                .generateEmbeddings(profile.id());

        assertThat(result.pending()).isTrue();
        assertThat(result.selfEmbeddingRecomputed()).isFalse();
        assertThat(result.preferenceEmbeddingRecomputed()).isFalse();
        verify(profiles, never()).save(any());
    }

    @Test
    void generateEmbeddings_keepsWhatSucceeded_whenOnlyOneCallFails() {
        Profile profile = profile("1001", ProfileEmbeddings.empty());
        when(profiles.findById(profile.id())).thenReturn(Optional.of(profile));
        when(embeddings.embed("self")).thenReturn(vector("self"));
        when(embeddings.embed("pref")).thenThrow(new EmbeddingGenerationException("down", null));

        EmbeddingGenerationResult result = new EmbeddingGenerationService(profiles, embeddings)
                .generateEmbeddings(profile.id());

        assertThat(result.pending()).isTrue();
        assertThat(result.selfEmbeddingRecomputed()).isTrue();
        ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).save(saved.capture());
        assertThat(saved.getValue().embeddings().hasSelfEmbedding()).isTrue();
        assertThat(saved.getValue().embeddings().hasPreferenceEmbedding()).isFalse();
    }

    // ---- background backfill -----------------------------------------------------------------

    @Test
    void backfill_completesRealUsersOnly_andStopsEarlyWhileProviderIsStillDown() {
        Profile realUser = profile("1001", ProfileEmbeddings.empty());
        Profile sample = profile(null, ProfileEmbeddings.empty());
        GenerateEmbeddingsUseCase useCase = org.mockito.Mockito.mock(GenerateEmbeddingsUseCase.class);
        when(profiles.findAll()).thenReturn(List.of(sample, realUser));
        when(useCase.generateEmbeddings(realUser.id())).thenReturn(new EmbeddingGenerationResult(false, false, true));

        new EmbeddingBackfillScheduler(profiles, useCase).completePendingEmbeddings();

        verify(useCase).generateEmbeddings(realUser.id());
        verify(useCase, never()).generateEmbeddings(sample.id());
    }

    // ---- preference refinement ---------------------------------------------------------------

    @Test
    void refine_whenChatModelDown_savesUserTextAsWritten_andStillReturnsRecommendations() {
        ProfileEmbeddings existing = new ProfileEmbeddings(vector("self"), vector("pref"));
        Profile profile = profile("1001", existing);
        when(profiles.findById(profile.id())).thenReturn(Optional.of(profile));
        when(chat.interpretPreferenceRefinement(anyString(), anyString())).thenThrow(new ChatCompletionException("down", null));
        when(recommendations.recommend(any())).thenReturn(List.of());

        RefinePreferenceResult result = new PreferenceRefinementService(
                profiles, chat, embeddings, updateLog, recommendations)
                .refine(new RefinePreferenceCommand(profile.id().value(), "I love hiking", 10, null));

        assertThat(result.updatedPreferenceDescription()).contains("pref").contains("I love hiking");
        assertThat(result.changeSummary()).contains("unavailable");
        ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).save(saved.capture());
        assertThat(saved.getValue().preferenceDescription()).contains("I love hiking");
        // the previous preference vector is kept so recommendations keep working (stale until backfilled)
        assertThat(saved.getValue().embeddings().hasPreferenceEmbedding()).isTrue();
        verify(embeddings, never()).embed(anyString()); // no second doomed call while the user waits
        verify(recommendations).recommend(any());
    }

    // ---- recommendations ---------------------------------------------------------------------

    @Test
    void recommend_whenRequesterHasNoEmbeddings_fallsBackToUnscoredCandidatesInsteadOfFailing() {
        RecommendationService service = new RecommendationService(profiles,
                new CompatibilityScorer(new ReciprocalHarmonicAggregationStrategy()));
        Profile requester = profile("1001", ProfileEmbeddings.empty());
        Profile candidate = profile("2002", ProfileEmbeddings.empty());
        when(profiles.findById(requester.id())).thenReturn(Optional.of(requester));
        when(profiles.findCandidates(any(), any())).thenReturn(List.of(candidate));

        List<RecommendationResult> results = service.recommend(new GetRecommendationsQuery(requester.id().value(), 5));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).candidateProfile().id()).isEqualTo(candidate.id());
        assertThat(results.get(0).match().aggregatedScore()).isZero();
    }

    private static EmbeddingVector vector(String sourceText) {
        return new EmbeddingVector(new float[] {1f, 0f}, MODEL, 2, TextHasher.sha256Hex(sourceText), Instant.now());
    }

    private static Profile profile(String telegramUserId, ProfileEmbeddings embeddings) {
        return Profile.builder()
                .id(ProfileId.generate()).telegramUserId(telegramUserId).displayName("Name").age(30)
                .gender(Gender.FEMALE).orientation(Orientation.STRAIGHT).seekingGenders(Set.of(Gender.MALE, Gender.FEMALE))
                .country("UA").city("Kyiv").selfDescription("self").preferenceDescription("pref")
                .embeddings(embeddings).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
}
