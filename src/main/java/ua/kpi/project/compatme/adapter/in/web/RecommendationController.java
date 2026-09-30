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
import ua.kpi.project.compatme.domain.model.LocationScope;

/**
 * Inbound REST adapter for retrieving top-N recommendations. Every candidate is scored via the
 * app's single compatibility-scoring method (reciprocal harmonic mean of bidirectional cosine
 * similarity) — there is no strategy-selection parameter. {@code scope} narrows the candidate
 * pool to the requester's own country/city (defaults to {@code GLOBAL}, i.e. no location
 * filtering).
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
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(defaultValue = "GLOBAL") LocationScope scope) {
        var results = recommendationUseCase.recommend(new GetRecommendationsQuery(profileId, topN, scope));
        var items = results.stream().map(mapper::toRecommendationItem).toList();
        return ResponseEntity.ok(new RecommendationsResponse(items));
    }
}
