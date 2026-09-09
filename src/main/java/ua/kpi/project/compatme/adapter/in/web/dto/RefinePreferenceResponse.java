package ua.kpi.project.compatme.adapter.in.web.dto;

import java.util.List;

public record RefinePreferenceResponse(
        String updatedPreferenceDescription,
        String changeSummary,
        List<RecommendationItem> recommendations) {
}
