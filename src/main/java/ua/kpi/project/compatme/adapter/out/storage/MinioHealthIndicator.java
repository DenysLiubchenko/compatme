package ua.kpi.project.compatme.adapter.out.storage;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Makes object-storage availability part of the application's readiness contract. */
@Component("minio")
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient client;
    private final String bucket;

    public MinioHealthIndicator(MinioClient client, MinioProperties properties) {
        this.client = client;
        this.bucket = properties.bucket();
    }

    @Override
    public Health health() {
        try {
            boolean bucketAvailable = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            return bucketAvailable ? Health.up().build() : Health.down().withDetail("reason", "bucket unavailable").build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
