package ua.kpi.project.compatme.application.port.in;

import ua.kpi.project.compatme.application.dto.GetRecommendationsQuery;
import ua.kpi.project.compatme.application.dto.RecommendationResult;

import java.util.List;

/**
 * Inbound use case for retrieving top-N compatibility recommendations for a profile, scored via
 * the app's single compatibility-scoring method (reciprocal harmonic mean — see
 * {@code domain.service.CompatibilityScorer}).
 */
public interface RecommendationUseCase {

    List<RecommendationResult> recommend(GetRecommendationsQuery query);
}
