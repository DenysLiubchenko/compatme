package ua.kpi.project.compatme.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ua.kpi.project.compatme.application.dto.GetRecommendationsQuery;
import ua.kpi.project.compatme.application.dto.RecommendationResult;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.in.RecommendationUseCase;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.CandidateMatch;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;
import ua.kpi.project.compatme.domain.service.CompatibilityScorer;

import java.util.Comparator;
import java.util.List;

/**
 * Application service implementing top-N recommendation retrieval.
 *
 * <p>Flow: (1) apply cheap hard filters (age range, mutual gender/seeking-gender match) via the
 * {@link ProfileRepositoryPort}, narrowing the candidate pool before any NLP scoring runs; then
 * (2) for candidates with complete cached embeddings, delegate to the framework-agnostic
 * {@link CompatibilityScorer} for the actual NLP-based directional compatibility scoring; then
 * (3) sort descending by aggregated score and return the top N.
 *
 * <p>For the dataset sizes expected in this thesis prototype (hundreds to low thousands of
 * profiles), scoring candidates in-memory in Java is sufficient and avoids the operational
 * complexity of MongoDB Atlas Vector Search, which may not be available on every plan.
 */
@Service
public class RecommendationService implements RecommendationUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final ProfileRepositoryPort profileRepository;
    private final CompatibilityScorer compatibilityScorer;

    public RecommendationService(ProfileRepositoryPort profileRepository, CompatibilityScorer compatibilityScorer) {
        this.profileRepository = profileRepository;
        this.compatibilityScorer = compatibilityScorer;
    }

    @Override
    public List<RecommendationResult> recommend(GetRecommendationsQuery query) {
        ProfileId requesterId = ProfileId.of(query.requesterId());
        Profile requester = profileRepository.findById(requesterId)
                .orElseThrow(() -> new ProfileNotFoundException(requesterId.value()));

        if (!requester.embeddings().isComplete()) {
            throw new IllegalStateException(
                    "Requester profile " + requesterId + " has no embeddings yet; "
                            + "call the embedding generation endpoint first");
        }

        ProfileRepositoryPort.CandidateFilter filter = ageFilterCenteredOn(requester);
        List<Profile> candidates = profileRepository.findCandidates(requesterId, filter);

        return candidates.stream()
                .filter(candidate -> mutuallyMatchesGenderPreference(requester, candidate))
                .filter(candidate -> hasScorableEmbeddings(requester, candidate))
                .map(candidate -> {
                    CandidateMatch match = compatibilityScorer.score(requester, candidate, query.strategy());
                    return new RecommendationResult(candidate, match);
                })
                .sorted(Comparator.comparingDouble((RecommendationResult r) -> r.match().aggregatedScore()).reversed())
                .limit(query.topN())
                .toList();
    }

    private boolean mutuallyMatchesGenderPreference(Profile requester, Profile candidate) {
        return requester.mutuallyMatchesSeekingGender(candidate);
    }

    private boolean hasScorableEmbeddings(Profile requester, Profile candidate) {
        if (!candidate.embeddings().isComplete()) {
            log.debug("Skipping candidate {} from recommendations for {}: embeddings not yet generated",
                    candidate.id(), requester.id());
            return false;
        }
        return true;
    }

    /**
     * A permissive, non-restrictive default age band around the requester. Kept simple for
     * thesis-prototype scope; could be replaced with an explicit user-configured range.
     */
    private ProfileRepositoryPort.CandidateFilter ageFilterCenteredOn(Profile requester) {
        if (requester.age() == null) {
            return ProfileRepositoryPort.CandidateFilter.none();
        }
        return new ProfileRepositoryPort.CandidateFilter(requester.age() - 15, requester.age() + 15);
    }
}
