package ua.kpi.project.compatme.domain.model;

/**
 * Selects how broadly the recommendation candidate pool is scoped geographically, relative to
 * the requesting profile's own {@code country}/{@code city}. Purely a pre-filter applied before
 * any NLP compatibility scoring runs — it never influences {@code CompatibilityScorer} or any
 * {@code CompatibilityAggregationStrategy}.
 */
public enum LocationScope {

    /** No location filtering — candidates from anywhere are considered. */
    GLOBAL,

    /** Only candidates in the same country as the requester. */
    COUNTRY,

    /** Only candidates in the same city (and therefore same country) as the requester. */
    CITY
}
