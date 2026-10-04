package ua.kpi.project.compatme.domain.model;

/**
 * Explicit, user-chosen search scope for a recommendation request, relative to the requester's
 * own {@link Location}. It is a hard pre-filter applied before NLP compatibility scoring; it is
 * never inferred automatically and never acts as a fallback or ranking boost.
 */
public enum LocationScope {

    /** Only candidates with the same city AND country as the requester. */
    CITY,

    /** Only candidates in the same country as the requester (city may differ). */
    COUNTRY,

    /** No location filtering at all. */
    WORLDWIDE
}
