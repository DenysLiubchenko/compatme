package ua.kpi.project.compatme.domain.model;

/**
 * Basic gender metadata used for hard pre-filtering of the candidate pool before NLP-based
 * compatibility scoring is applied. Kept intentionally small for thesis-prototype scope.
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER
}
