package ua.kpi.project.compatme.adapter.out.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ua.kpi.project.compatme.application.exception.PhotoNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against a real MinIO container; skipped automatically when Docker is unavailable. */
@Testcontainers(disabledWithoutDocker = true)
class MinioPhotoStorageAdapterTest {

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("minio/minio:latest")
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", "testuser")
            .withEnv("MINIO_ROOT_PASSWORD", "testpassword")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    static MinioPhotoStorageAdapter adapter;

    @BeforeAll
    static void setUp() {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        MinioProperties props = new MinioProperties(endpoint, "testuser", "testpassword", "it-bucket");
        MinioClient client = MinioClient.builder().endpoint(endpoint)
                .credentials(props.accessKey(), props.secretKey()).build();
        adapter = new MinioPhotoStorageAdapter(client, props);
        adapter.ensureBucketOnStartup();
    }

    @Test
    void store_retrieve_delete_roundTrip() {
        byte[] bytes = {1, 2, 3, 4, 5};

        String urn = adapter.store(bytes, "image/png");

        assertThat(urn).matches("profile-photos/[0-9a-f-]{36}\\.png");
        assertThat(adapter.retrieve(urn)).isEqualTo(bytes);

        adapter.delete(urn);

        assertThatThrownBy(() -> adapter.retrieve(urn)).isInstanceOf(PhotoNotFoundException.class);
    }

    @Test
    void store_derivesExtensionFromContentType() {
        assertThat(adapter.store(new byte[] {1}, "image/jpeg")).endsWith(".jpg");
        assertThat(adapter.store(new byte[] {1}, "image/webp")).endsWith(".webp");
    }

    @Test
    void delete_unknownUrn_isNoOp() {
        adapter.delete("profile-photos/does-not-exist.jpg");
    }
}
