package ua.kpi.project.compatme.application.port.out;

/**
 * Outbound port for binary photo storage. Framework-agnostic: implementations (e.g. MinIO/S3)
 * live in {@code adapter.out.storage}. Implementations only store and fetch bytes; deciding whether
 * a photo is acceptable is an application-layer concern.
 */
public interface PhotoStoragePort {

    /**
     * Stores the photo and returns an opaque URN/key to be kept in {@code Profile.photoUrns}.
     *
     * @throws ua.kpi.project.compatme.application.exception.PhotoStorageException on storage failure
     */
    String store(byte[] photoBytes, String contentType);

    /**
     * @throws ua.kpi.project.compatme.application.exception.PhotoNotFoundException if the URN is unknown
     * @throws ua.kpi.project.compatme.application.exception.PhotoStorageException on storage failure
     */
    byte[] retrieve(String urn);

    /** Removes the photo. Deleting an unknown URN is a no-op. */
    void delete(String urn);
}
