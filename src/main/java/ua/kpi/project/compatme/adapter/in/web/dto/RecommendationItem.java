package ua.kpi.project.compatme.adapter.in.web.dto;

import ua.kpi.project.compatme.domain.model.AggregationStrategyType;

import java.util.List;

/**
 * A single recommended candidate in a {@link RecommendationsResponse}.
 */
public record RecommendationItem(
        String candidateId,
        String displayName,
        Integer age,

        /** Optional photo URL of the candidate — only the URL, never image bytes. */
        String photoUrl,

        /** Telegram {@code file_id} references for the candidate's uploaded photos, if any. */
        List<String> photoFileIds,

        /** Optional free-text country/city, for rendering a candidate's location on a card. */
        String country,
        String city,

        String selfDescription,
        String preferenceDescription,

        double scoreAtoB,
        double scoreBtoA,
        double selfSelfSimilarity,
        AggregationStrategyType strategy,
        double aggregatedScore) {
}
