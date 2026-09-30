package ua.kpi.project.compatme.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.kpi.project.compatme.domain.service.CompatibilityAggregationStrategy;
import ua.kpi.project.compatme.domain.service.CompatibilityScorer;
import ua.kpi.project.compatme.domain.service.ReciprocalHarmonicAggregationStrategy;

/**
 * Wires the domain's single {@link CompatibilityAggregationStrategy} implementation (reciprocal
 * harmonic mean) into a {@link CompatibilityScorer} bean. This is the ONLY place these plain-Java
 * domain classes are touched by a Spring {@code @Configuration} — they have zero Spring
 * dependencies and remain independently unit-testable without this class ever being loaded.
 */
@Configuration
public class CompatibilityScoringConfig {

    @Bean
    public CompatibilityAggregationStrategy compatibilityAggregationStrategy() {
        return new ReciprocalHarmonicAggregationStrategy();
    }

    @Bean
    public CompatibilityScorer compatibilityScorer(CompatibilityAggregationStrategy compatibilityAggregationStrategy) {
        return new CompatibilityScorer(compatibilityAggregationStrategy);
    }
}
