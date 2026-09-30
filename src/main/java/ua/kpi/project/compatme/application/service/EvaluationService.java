package ua.kpi.project.compatme.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ua.kpi.project.compatme.application.dto.EvaluationReport;
import ua.kpi.project.compatme.application.port.in.EvaluateCompatibilityScoringUseCase;
import ua.kpi.project.compatme.application.port.out.GroundTruthPairRepositoryPort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.GroundTruthLabel;
import ua.kpi.project.compatme.domain.model.GroundTruthPair;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.service.CompatibilityScorer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the thesis's core evaluation artifact: for every stored {@link GroundTruthPair},
 * scores the pair via the app's single {@link CompatibilityScorer} (reciprocal harmonic mean —
 * no scoring logic is duplicated here), then reports the average aggregated score per expected
 * label (MUTUAL_MATCH / ONE_SIDED / NO_MATCH). A higher average for MUTUAL_MATCH than for
 * ONE_SIDED/NO_MATCH is the empirical justification for choosing reciprocal harmonic aggregation.
 *
 * <p>A pair is skipped (and excluded from every average/count) if either profile cannot be found
 * or does not yet have complete embeddings — the same embedding-completeness precondition
 * {@link CompatibilityScorer} itself enforces elsewhere. Skips are logged at DEBUG so a run
 * against a partially-embedded dataset doesn't spam the console.
 *
 * <p>Because {@link ua.kpi.project.compatme.domain.model.DirectionalScores} and the aggregation
 * strategy are symmetric functions of the two directional scores, scoring (A, B) vs. (B, A)
 * yields the same aggregated score — so which profile is passed as "requester" vs. "candidate"
 * does not affect the result.
 */
@Service
public class EvaluationService implements EvaluateCompatibilityScoringUseCase {

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
        Map<GroundTruthLabel, Double> sums = newLabelMap();
        Map<GroundTruthLabel, Integer> counts = new EnumMap<>(GroundTruthLabel.class);
        for (GroundTruthLabel label : GroundTruthLabel.values()) {
            counts.put(label, 0);
        }

        List<GroundTruthPair> pairs = groundTruthPairRepository.findAll();
        for (GroundTruthPair pair : pairs) {
            accumulate(pair, sums, counts);
        }

        Map<GroundTruthLabel, Double> averages = computeAverages(sums, counts);
        EvaluationReport report = new EvaluationReport(averages, counts);
        logSummary(report);
        return report;
    }

    private void accumulate(GroundTruthPair pair, Map<GroundTruthLabel, Double> sums, Map<GroundTruthLabel, Integer> counts) {
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
        double aggregatedScore = compatibilityScorer.score(a, b).aggregatedScore();
        sums.merge(label, aggregatedScore, Double::sum);
        counts.merge(label, 1, Integer::sum);
    }

    private Map<GroundTruthLabel, Double> computeAverages(Map<GroundTruthLabel, Double> sums, Map<GroundTruthLabel, Integer> counts) {
        Map<GroundTruthLabel, Double> averages = newLabelMap();
        for (GroundTruthLabel label : GroundTruthLabel.values()) {
            int count = counts.get(label);
            double average = count == 0 ? 0.0 : sums.get(label) / count;
            averages.put(label, average);
        }
        return averages;
    }

    private Map<GroundTruthLabel, Double> newLabelMap() {
        Map<GroundTruthLabel, Double> map = new EnumMap<>(GroundTruthLabel.class);
        for (GroundTruthLabel label : GroundTruthLabel.values()) {
            map.put(label, 0.0);
        }
        return map;
    }

    /** Plain-text summary at INFO level, for screenshotting into the thesis without a JSON viewer. */
    private void logSummary(EvaluationReport report) {
        StringBuilder summary = new StringBuilder("Compatibility scoring evaluation report:\n");
        for (GroundTruthLabel label : GroundTruthLabel.values()) {
            summary.append(String.format("  %-14s (n=%d): avgScore=%.3f%n",
                    label, report.pairCounts().get(label), report.averageScoreByLabel().get(label)));
        }
        log.info(summary.toString());
    }
}
