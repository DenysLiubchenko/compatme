package ua.kpi.project.compatme.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kpi.project.compatme.adapter.in.web.dto.EvaluationReportResponse;
import ua.kpi.project.compatme.application.dto.EvaluationReport;
import ua.kpi.project.compatme.application.port.in.EvaluateAggregationStrategiesUseCase;

/**
 * Inbound REST adapter exposing the thesis evaluation report: for every stored ground-truth pair,
 * the average aggregated compatibility score per (expected label, aggregation strategy)
 * combination. Read-only and side-effect-free (besides the INFO-level summary log emitted by the
 * use case) — safe to call repeatedly while iterating on the dataset.
 */
@RestController
@RequestMapping("/api/v1/evaluation")
public class EvaluationController {

    private final EvaluateAggregationStrategiesUseCase evaluateAggregationStrategiesUseCase;

    public EvaluationController(EvaluateAggregationStrategiesUseCase evaluateAggregationStrategiesUseCase) {
        this.evaluateAggregationStrategiesUseCase = evaluateAggregationStrategiesUseCase;
    }

    @GetMapping("/report")
    public ResponseEntity<EvaluationReportResponse> getReport() {
        EvaluationReport report = evaluateAggregationStrategiesUseCase.generateReport();
        return ResponseEntity.ok(new EvaluationReportResponse(report.resultsByLabelAndStrategy(), report.pairCounts()));
    }
}
