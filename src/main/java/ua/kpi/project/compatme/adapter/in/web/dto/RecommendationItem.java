package ua.kpi.project.compatme.adapter.in.web.dto;

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

        /** Optional free-text country/city, for rendering a candidate's location on a card. */
        String country,
        String city,

        String selfDescription,
        String preferenceDescription,

        double scoreAtoB,
        double scoreBtoA,
        double selfSelfSimilarity,
        double aggregatedScore) {
}
