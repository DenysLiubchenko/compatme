package ua.kpi.project.compatme.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import ua.kpi.project.compatme.adapter.out.geocoding.NominatimProperties;

/**
 * Enables {@link NominatimProperties} binding from {@code application.yml}. No beans needed
 * beyond that — {@code NominatimReverseGeocodingAdapter} is a plain {@code @Component} that
 * takes the properties bean directly, mirroring the Gemini adapters' style.
 */
@Configuration
@EnableConfigurationProperties(NominatimProperties.class)
public class NominatimConfig {
}
