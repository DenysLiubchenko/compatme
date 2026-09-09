package ua.kpi.project.compatme.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ua.kpi.project.compatme.adapter.in.web.dto.RecommendationsResponse;
import ua.kpi.project.compatme.application.dto.GetRecommendationsQuery;
import ua.kpi.project.compatme.application.port.in.RecommendationUseCase;
import ua.kpi.project.compatme.domain.model.AggregationStrategyType;

/**
 * Inbound REST adapter for retrieving top-N recommendations. The aggregation strategy is
 * selectable via a query parameter (defaulting to the reciprocal harmonic-mean strategy), to
 * support the thesis's A/B comparison between aggregation modes without needing separate
 * endpoints per strategy.
 */
@RestController
@RequestMapping("/api/v1/profiles")
public class RecommendationController {

    private final RecommendationUseCase recommendationUseCase;
    private final ProfileWebMapper mapper;

    public RecommendationController(RecommendationUseCase recommendationUseCase, ProfileWebMapper mapper) {
        this.recommendationUseCase = recommendationUseCase;
        this.mapper = mapper;
    }

    @GetMapping("/{profileId}/recommendations")
    public ResponseEntity<RecommendationsResponse> getRecommendations(
            @PathVariable String profileId,
            @RequestParam(defaultValue = "RECIPROCAL_HARMONIC") AggregationStrategyType strategy,
            @RequestParam(defaultValue = "10") int topN) {
        var results = recommendationUseCase.recommend(new GetRecommendationsQuery(profileId, strategy, topN));
        var items = results.stream().map(mapper::toRecommendationItem).toList();
        return ResponseEntity.ok(new RecommendationsResponse(items));
    }
}
