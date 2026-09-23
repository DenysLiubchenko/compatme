package ua.kpi.project.compatme.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Describes the metadata (title, version, contact, license) shown in the OpenAPI document that
 * springdoc-openapi generates at runtime from the {@code @RestController} classes in
 * {@code adapter.in.web}. No endpoint-level annotations are required for the spec to be
 * generated — springdoc infers paths, parameters and schemas directly from the Spring MVC
 * mappings and request/response DTOs; this bean only supplies the top-level document info.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI compatMeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CompatMe API")
                        .description("Intelligent dating/compatibility matching backend (university thesis project).")
                        .version("v1")
                        .contact(new Contact().name("CompatMe"))
                        .license(new License().name("Unlicensed")));
    }
}
