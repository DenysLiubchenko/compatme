package ua.kpi.project.compatme.adapter.in.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kpi.project.compatme.adapter.in.web.dto.RefinePreferenceRequest;
import ua.kpi.project.compatme.adapter.in.web.dto.RefinePreferenceResponse;
import ua.kpi.project.compatme.application.dto.RefinePreferenceCommand;
import ua.kpi.project.compatme.application.dto.RefinePreferenceResult;
import ua.kpi.project.compatme.application.port.in.PreferenceRefinementUseCase;

/**
 * Inbound REST adapter for submitting a natural-language preference refinement message and
 * receiving both the rewritten preference description and refreshed recommendations.
 */
@RestController
@RequestMapping("/api/v1/profiles")
public class PreferenceController {

    private final PreferenceRefinementUseCase preferenceRefinementUseCase;
    private final ProfileWebMapper mapper;

    public PreferenceController(PreferenceRefinementUseCase preferenceRefinementUseCase, ProfileWebMapper mapper) {
        this.preferenceRefinementUseCase = preferenceRefinementUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/{profileId}/preference-refinements")
    public ResponseEntity<RefinePreferenceResponse> refinePreference(
            @PathVariable String profileId, @Valid @RequestBody RefinePreferenceRequest request) {
        RefinePreferenceResult result = preferenceRefinementUseCase.refine(new RefinePreferenceCommand(
                profileId, request.message(), request.strategyOrDefault(), request.topNOrDefault()));

        var items = result.recommendations().stream().map(mapper::toRecommendationItem).toList();
        return ResponseEntity.ok(new RefinePreferenceResponse(
                result.updatedPreferenceDescription(), result.changeSummary(), items));
    }
}
