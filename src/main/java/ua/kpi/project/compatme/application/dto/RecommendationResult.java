package ua.kpi.project.compatme.application.dto;

import ua.kpi.project.compatme.domain.model.CandidateMatch;
import ua.kpi.project.compatme.domain.model.Profile;

/**
 * A single recommendation result: the candidate's domain match data bundled with enough profile
 * summary data for the web adapter to render a response, without the web layer needing to know
 * about {@code CandidateMatch} internals directly.
 */
public record RecommendationResult(Profile candidateProfile, CandidateMatch match) {
}
