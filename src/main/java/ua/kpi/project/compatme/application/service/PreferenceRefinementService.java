package ua.kpi.project.compatme.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ua.kpi.project.compatme.application.dto.GetRecommendationsQuery;
import ua.kpi.project.compatme.application.dto.RecommendationResult;
import ua.kpi.project.compatme.application.dto.RefinePreferenceCommand;
import ua.kpi.project.compatme.application.dto.RefinePreferenceResult;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.in.PreferenceRefinementUseCase;
import ua.kpi.project.compatme.application.port.in.RecommendationUseCase;
import ua.kpi.project.compatme.application.port.out.ChatCompletionPort;
import ua.kpi.project.compatme.application.port.out.EmbeddingProviderPort;
import ua.kpi.project.compatme.application.port.out.PreferenceUpdateLogPort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.EmbeddingVector;
import ua.kpi.project.compatme.domain.model.PreferenceRefinementResult;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileEmbeddings;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;
import java.util.List;

/**
 * Application service implementing natural-language preference refinement
 * ("UpdatePreferencesUseCase" in the thesis spec).
 *
 * <p>Flow: (1) call the chat model via {@link ChatCompletionPort} to interpret the user's
 * free-text refinement message against their current preference description; (2) persist the
 * rewritten {@code preferenceDescription}; (3) re-embed <em>only</em> that field via
 * {@link EmbeddingProviderPort} — the {@code selfDescription} embedding is left untouched and
 * every other candidate's embeddings are reused as-is, so the existing candidate pool is
 * re-ranked without recomputing every embedding from scratch; (4) append an entry to the
 * preference-update audit log; (5) delegate to {@link RecommendationUseCase} to return refreshed
 * recommendations.
 */
@Service
public class PreferenceRefinementService implements PreferenceRefinementUseCase {

    private static final Logger log = LoggerFactory.getLogger(PreferenceRefinementService.class);

    private final ProfileRepositoryPort profileRepository;
    private final ChatCompletionPort chatCompletionPort;
    private final EmbeddingProviderPort embeddingProvider;
    private final PreferenceUpdateLogPort preferenceUpdateLog;
    private final RecommendationUseCase recommendationUseCase;

    public PreferenceRefinementService(
            ProfileRepositoryPort profileRepository,
            ChatCompletionPort chatCompletionPort,
            EmbeddingProviderPort embeddingProvider,
            PreferenceUpdateLogPort preferenceUpdateLog,
            RecommendationUseCase recommendationUseCase) {
        this.profileRepository = profileRepository;
        this.chatCompletionPort = chatCompletionPort;
        this.embeddingProvider = embeddingProvider;
        this.preferenceUpdateLog = preferenceUpdateLog;
        this.recommendationUseCase = recommendationUseCase;
    }

    @Override
    public RefinePreferenceResult refine(RefinePreferenceCommand command) {
        ProfileId requesterId = ProfileId.of(command.requesterId());
        Profile profile = profileRepository.findById(requesterId)
                .orElseThrow(() -> new ProfileNotFoundException(requesterId.value()));

        String previousPreferenceDescription = profile.preferenceDescription();

        PreferenceRefinementResult interpretation = chatCompletionPort.interpretPreferenceRefinement(
                previousPreferenceDescription, command.message());

        Instant now = Instant.now();
        Profile withUpdatedText = profile.withPreferenceDescription(interpretation.updatedPreferenceDescription(), now);

        EmbeddingVector newPreferenceEmbedding = embeddingProvider.embed(interpretation.updatedPreferenceDescription());
        Profile withUpdatedEmbedding = withUpdatedText.withEmbeddings(
                new ProfileEmbeddings(withUpdatedText.embeddings().selfEmbedding(), newPreferenceEmbedding), now);

        profileRepository.save(withUpdatedEmbedding);

        preferenceUpdateLog.logUpdate(new PreferenceUpdateLogPort.PreferenceUpdateLogEntry(
                requesterId,
                previousPreferenceDescription,
                interpretation.updatedPreferenceDescription(),
                command.message(),
                interpretation.changeSummary(),
                now));

        log.info("Preference refined for profile {}: {}", requesterId, interpretation.changeSummary());

        List<RecommendationResult> recommendations = recommendationUseCase.recommend(
                new GetRecommendationsQuery(command.requesterId(), command.strategy(), command.topN()));

        return new RefinePreferenceResult(
                interpretation.updatedPreferenceDescription(), interpretation.changeSummary(), recommendations);
    }
}
