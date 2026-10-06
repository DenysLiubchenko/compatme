package ua.kpi.project.compatme.adapter.out.storage;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioClientConfig {

    @Bean
    MinioClient minioClient(MinioProperties properties) {
        if (isBlank(properties.accessKey()) || isBlank(properties.secretKey())) {
            throw new IllegalStateException(
                    "MinIO credentials are required: set MINIO_ACCESS_KEY and MINIO_SECRET_KEY");
        }
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
