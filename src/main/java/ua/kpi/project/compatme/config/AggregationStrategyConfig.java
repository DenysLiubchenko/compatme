package ua.kpi.project.compatme.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.kpi.project.compatme.domain.model.AggregationStrategyType;
import ua.kpi.project.compatme.domain.service.CompatibilityAggregationStrategy;
import ua.kpi.project.compatme.domain.service.CompatibilityScorer;
import ua.kpi.project.compatme.domain.service.ReciprocalHarmonicAggregationStrategy;
import ua.kpi.project.compatme.domain.service.SimpleAverageAggregationStrategy;
import ua.kpi.project.compatme.domain.service.SimpleSelfSimilarityAggregationStrategy;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Wires the domain's {@link CompatibilityAggregationStrategy} implementations into a
 * {@link CompatibilityScorer} bean. This is the ONLY place these plain-Java domain classes are
 * touched by a Spring {@code @Configuration} — the strategies themselves have zero Spring
 * dependencies and remain independently unit-testable without this class ever being loaded.
 */
@Configuration
public class AggregationStrategyConfig {

    @Bean
    public CompatibilityScorer compatibilityScorer() {
        Map<AggregationStrategyType, CompatibilityAggregationStrategy> strategies = Stream.of(
                        new SimpleAverageAggregationStrategy(),
                        new SimpleSelfSimilarityAggregationStrategy(),
                        new ReciprocalHarmonicAggregationStrategy())
                .collect(Collectors.toMap(CompatibilityAggregationStrategy::type, Function.identity()));
        return new CompatibilityScorer(strategies);
    }
}
