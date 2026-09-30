package ua.kpi.project.compatme.application.port.in;

import ua.kpi.project.compatme.application.dto.EvaluationReport;

/**
 * Use case for the thesis's core evaluation artifact: scoring every stored ground-truth pair via
 * the app's single compatibility-scoring method (reciprocal harmonic mean) and reporting the
 * average aggregated score per expected label (MUTUAL_MATCH / ONE_SIDED / NO_MATCH).
 */
public interface EvaluateCompatibilityScoringUseCase {

    EvaluationReport generateReport();
}
