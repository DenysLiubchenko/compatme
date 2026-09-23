package ua.kpi.project.compatme.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ua.kpi.project.compatme.application.dto.EvaluationReport;
import ua.kpi.project.compatme.application.port.in.EvaluateAggregationStrategiesUseCase;
import ua.kpi.project.compatme.application.port.out.GroundTruthPairRepositoryPort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.model.GroundTruthLabel;
import ua.kpi.project.compatme.domain.model.GroundTruthPair;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.service.CompatibilityScorer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the thesis's core evaluation artifact: for every stored {@link GroundTruthPair},
 * scores the pair under each {@link AggregationStrategyType} (reusing the same
 * {@link CompatibilityScorer} the live recommendation flow uses — no scoring logic is
 * duplicated), then reports the average aggregated score per (expected label, strategy)
 * combination.
 *
 * <p>A pair is skipped (and excluded from every average/count) if either profile cannot be found
 * or does not yet have complete embeddings — the same embedding-completeness precondition
 * {@link CompatibilityScorer} itself enforces elsewhere. Skips are logged at DEBUG so a run
 * against a partially-embedded dataset doesn't spam the console.
 *
 * <p>Because {@link ua.kpi.project.compatme.domain.model.DirectionalScores} and every
 * {@link ua.kpi.project.compatme.domain.service.CompatibilityAggregationStrategy} are symmetric
 * functions of the two directional scores, scoring (A, B) vs. (B, A) under the same strategy
 * yields the same aggregated score — so which profile is passed as "requester" vs. "candidate"
 * does not affect the result.
 */
@Service
public class EvaluationService implements EvaluateAggregationStrategiesUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final GroundTruthPairRepositoryPort groundTruthPairRepository;
    private final ProfileRepositoryPort profileRepository;
    private final CompatibilityScorer compatibilityScorer;

    public EvaluationService(
            GroundTruthPairRepositoryPort groundTruthPairRepository,
            ProfileRepositoryPort profileRepository,
            CompatibilityScorer compatibilityScorer) {
        this.groundTruthPairRepository = groundTruthPairRepository;
        this.profileRepository = profileRepository;
        this.compatibilityScorer = compatibilityScorer;
    }

    @Override
    public EvaluationReport generateReport() {
        Map<GroundTruthLabel, Map<AggregationStrategyType, Double>> sums = newLabelStrategyMap();
        Map<GroundTruthLabel, Integer> counts = new EnumMap<>(GroundTruthLabel.class);
        for (GroundTruthLabel label : GroundTruthLabel.values()) {
            counts.put(label, 0);
        }

        List<GroundTruthPair> pairs = groundTruthPairRepository.findAll();
        for (GroundTruthPair pair : pairs) {
            accumulate(pair, sums, counts);
        }

        Map<GroundTruthLabel, Map<AggregationStrategyType, Double>> averages = computeAverages(sums, counts);
        EvaluationReport report = new EvaluationReport(averages, counts);
        logSummary(report);
        return report;
    }

    private void accumulate(
            GroundTruthPair pair,
            Map<GroundTruthLabel, Map<AggregationStrategyType, Double>> sums,
            Map<GroundTruthLabel, Integer> counts) {
        var profileA = profileRepository.findById(pair.profileAId());
        var profileB = profileRepository.findById(pair.profileBId());
        if (profileA.isEmpty() || profileB.isEmpty()) {
            log.debug("Skipping ground-truth pair ({}, {}): one or both profiles not found",
                    pair.profileAId(), pair.profileBId());
            return;
        }
        Profile a = profileA.get();
        Profile b = profileB.get();
        if (!a.embeddings().isComplete() || !b.embeddings().isComplete()) {
            log.debug("Skipping ground-truth pair ({}, {}): embeddings not complete for one or both profiles",
                    pair.profileAId(), pair.profileBId());
            return;
        }

        GroundTruthLabel label = pair.expectedLabel();
        Map<AggregationStrategyType, Double> labelSums = sums.get(label);
        for (AggregationStrategyType strategy : AggregationStrategyType.values()) {
            double aggregatedScore = compatibilityScorer.score(a, b, strategy).aggregatedScore();
            labelSums.merge(strategy, aggregatedScore, Double::sum);
        }
        counts.merge(label, 1, Integer::sum);
    }

    private Map<GroundTruthLabel, Map<AggregationStrategyType, Double>> computeAverages(
            Map<GroundTruthLabel, Map<AggregationStrategyType, Double>> sums, Map<GroundTruthLabel, Integer> counts) {
        Map<GroundTruthLabel, Map<AggregationStrategyType, Double>> averages = newLabelStrategyMap();
        for (GroundTruthLabel label : GroundTruthLabel.values()) {
            int count = counts.get(label);
            for (AggregationStrategyType strategy : AggregationStrategyType.values()) {
                double average = count == 0 ? 0.0 : sums.get(label).get(strategy) / count;
                averages.get(label).put(strategy, average);
            }
        }
        return averages;
    }

    private Map<GroundTruthLabel, Map<AggregationStrategyType, Double>> newLabelStrategyMap() {
        Map<GroundTruthLabel, Map<AggregationStrategyType, Double>> map = new EnumMap<>(GroundTruthLabel.class);
        for (GroundTruthLabel label : GroundTruthLabel.values()) {
            Map<AggregationStrategyType, Double> strategyMap = new EnumMap<>(AggregationStrategyType.class);
            for (AggregationStrategyType strategy : AggregationStrategyType.values()) {
                strategyMap.put(strategy, 0.0);
            }
            map.put(label, strategyMap);
        }
        return map;
    }

    /** Plain-text summary at INFO level, for screenshotting into the thesis without a JSON viewer. */
    private void logSummary(EvaluationReport report) {
        StringBuilder summary = new StringBuilder("Aggregation strategy evaluation report:\n");
        for (GroundTruthLabel label : GroundTruthLabel.values()) {
            summary.append(String.format("  %-14s (n=%d): ", label, report.pairCounts().get(label)));
            for (AggregationStrategyType strategy : AggregationStrategyType.values()) {
                summary.append(String.format("%s=%.3f  ", strategy, report.resultsByLabelAndStrategy().get(label).get(strategy)));
            }
            summary.append('\n');
        }
        log.info(summary.toString());
    }
}
