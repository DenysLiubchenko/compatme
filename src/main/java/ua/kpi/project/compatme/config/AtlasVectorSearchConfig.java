package ua.kpi.project.compatme.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import ua.kpi.project.compatme.adapter.out.persistence.AtlasVectorSearchProperties;

@Configuration
@EnableConfigurationProperties(AtlasVectorSearchProperties.class)
public class AtlasVectorSearchConfig {
}
