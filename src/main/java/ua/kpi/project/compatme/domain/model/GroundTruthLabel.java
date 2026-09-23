package ua.kpi.project.compatme.domain.model;

/**
 * The expected reciprocal-compatibility label for a {@link GroundTruthPair} in the thesis
 * evaluation dataset. Assigned by the synthetic dataset generator (or by hand), never derived
 * from the scoring logic itself — it is the "answer key" the evaluation use case compares
 * aggregation-strategy output against.
 */
public enum GroundTruthLabel {

    /** Both profiles are expected to be genuinely, reciprocally interested in each other. */
    MUTUAL_MATCH,

    /** Only one side is expected to be interested (interest is not reciprocated). */
    ONE_SIDED,

    /** Neither side is expected to be a good match for the other. */
    NO_MATCH
}
