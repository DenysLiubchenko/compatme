package ua.kpi.project.compatme.adapter.telegram.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileCardFormatterTest {

    @Test
    void formatRecommendations_showsPreciseAggregateAndRequesterScores() {
        String result = ProfileCardFormatter.formatRecommendations(List.of(Map.of(
                "displayName", "Yu",
                "aggregatedScore", 0.81234,
                "scoreAtoB", 0.80678)));

        assertThat(result).isEqualTo("• Yu (score: 0.8123, your fit: 0.8068)");
    }
}
