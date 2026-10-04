package ua.kpi.project.compatme.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.kpi.project.compatme.application.config.PhotoPolicyProperties;

import java.util.List;

/**
 * Binds {@code photo-storage.*} (the acceptance rules) and exposes them to the application layer
 * as a Spring-free {@link PhotoPolicyProperties}. The nested {@code photo-storage.minio.*} keys are
 * bound separately by the storage adapter.
 */
@Configuration
@EnableConfigurationProperties(PhotoStorageConfig.PhotoStorageProperties.class)
public class PhotoStorageConfig {

    @ConfigurationProperties(prefix = "photo-storage")
    public record PhotoStorageProperties(
            @DefaultValue("5242880") long maxSizeBytes,
            @DefaultValue({"image/jpeg", "image/png", "image/webp"}) List<String> allowedContentTypes,
            @DefaultValue("6") int maxPhotosPerProfile) {
    }

    @Bean
    PhotoPolicyProperties photoPolicyProperties(PhotoStorageProperties properties) {
        return new PhotoPolicyProperties(
                properties.maxSizeBytes(), properties.allowedContentTypes(), properties.maxPhotosPerProfile());
    }
}
