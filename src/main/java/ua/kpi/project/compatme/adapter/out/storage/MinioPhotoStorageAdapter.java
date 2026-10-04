package ua.kpi.project.compatme.adapter.out.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.application.exception.PhotoNotFoundException;
import ua.kpi.project.compatme.application.exception.PhotoStorageException;
import ua.kpi.project.compatme.application.port.out.PhotoStoragePort;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * {@link PhotoStoragePort} backed by MinIO (S3-compatible). The only class that touches the MinIO
 * SDK. It stores bytes as-is; whether a photo is acceptable is decided in the application layer.
 */
@Component
public class MinioPhotoStorageAdapter implements PhotoStoragePort {

    static final String KEY_PREFIX = "profile-photos/";

    private static final Logger log = LoggerFactory.getLogger(MinioPhotoStorageAdapter.class);

    private final MinioClient client;
    private final String bucket;

    public MinioPhotoStorageAdapter(MinioClient client, MinioProperties properties) {
        this.client = client;
        this.bucket = properties.bucket();
    }

    /**
     * Creates the bucket if missing. A failure (e.g. MinIO not up yet) is logged rather than
     * fatal, so the rest of the app still starts; the bucket check is retried lazily on first use.
     */
    @PostConstruct
    void ensureBucketOnStartup() {
        try {
            ensureBucket();
        } catch (PhotoStorageException e) {
            log.warn("Could not verify MinIO bucket '{}' at startup; will retry on first use", bucket, e);
        }
    }

    @Override
    public String store(byte[] photoBytes, String contentType) {
        String urn = KEY_PREFIX + UUID.randomUUID() + "." + extensionFor(contentType);
        try {
            ensureBucket();
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(urn)
                    .stream(new ByteArrayInputStream(photoBytes), photoBytes.length, -1)
                    .contentType(contentType)
                    .build());
            return urn;
        } catch (PhotoStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new PhotoStorageException("Failed to store photo", e);
        }
    }

    @Override
    public byte[] retrieve(String urn) {
        try (GetObjectResponse response = client.getObject(
                GetObjectArgs.builder().bucket(bucket).object(urn).build())) {
            return response.readAllBytes();
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new PhotoNotFoundException(urn);
            }
            throw new PhotoStorageException("Failed to retrieve photo " + urn, e);
        } catch (IOException | java.security.GeneralSecurityException | io.minio.errors.MinioException e) {
            throw new PhotoStorageException("Failed to retrieve photo " + urn, e);
        }
    }

    @Override
    public void delete(String urn) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(urn).build());
        } catch (Exception e) {
            throw new PhotoStorageException("Failed to delete photo " + urn, e);
        }
    }

    private synchronized void ensureBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket '{}'", bucket);
            }
        } catch (Exception e) {
            throw new PhotoStorageException("Failed to verify/create bucket " + bucket, e);
        }
    }

    static String extensionFor(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "bin";
        };
    }
}
