package ua.kpi.project.compatme.application.port.in;

import ua.kpi.project.compatme.application.dto.EvaluationReport;

/**
 * Use case for the thesis's core evaluation artifact: scoring every stored ground-truth pair
 * under each {@link ua.kpi.project.compatme.domain.model.AggregationStrategyType} and reporting
 * the average aggregated score per (expected label, strategy) combination.
 */
public interface EvaluateAggregationStrategiesUseCase {

    EvaluationReport generateReport();
}
