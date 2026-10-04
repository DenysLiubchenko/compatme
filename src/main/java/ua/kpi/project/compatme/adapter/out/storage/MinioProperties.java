package ua.kpi.project.compatme.adapter.out.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Connection settings for MinIO, bound from {@code photo-storage.minio.*}. */
@ConfigurationProperties(prefix = "photo-storage.minio")
public record MinioProperties(
        @DefaultValue("http://localhost:9000") String endpoint,
        String accessKey,
        String secretKey,
        @DefaultValue("compatme-photos") String bucket) {
}
