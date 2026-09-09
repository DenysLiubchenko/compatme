package ua.kpi.project.compatme.domain.model;

/**
 * The result of scoring one candidate profile against the requesting profile under a given
 * aggregation strategy. Kept in the domain layer since it is a pure function of the domain
 * scoring logic, with no framework or transport concerns.
 */
public record CandidateMatch(
        ProfileId candidateId,
        DirectionalScores directionalScores,
        AggregationStrategyType strategy,
        double aggregatedScore) {
}
