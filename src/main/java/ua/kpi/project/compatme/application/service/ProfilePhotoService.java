package ua.kpi.project.compatme.application.service;

import org.springframework.stereotype.Service;
import ua.kpi.project.compatme.application.config.PhotoPolicyProperties;
import ua.kpi.project.compatme.application.exception.PhotoNotFoundException;
import ua.kpi.project.compatme.application.exception.PhotoTooLargeException;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.exception.TooManyPhotosException;
import ua.kpi.project.compatme.application.exception.UnsupportedPhotoTypeException;
import ua.kpi.project.compatme.application.port.in.ProfilePhotoUseCase;
import ua.kpi.project.compatme.application.port.out.PhotoStoragePort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Owns the photo business rules (size, type, count). Validation runs before any storage call so
 * it is testable without a storage backend.
 */
@Service
public class ProfilePhotoService implements ProfilePhotoUseCase {

    private final ProfileRepositoryPort profileRepository;
    private final PhotoStoragePort photoStorage;
    private final PhotoPolicyProperties policy;

    public ProfilePhotoService(ProfileRepositoryPort profileRepository, PhotoStoragePort photoStorage,
                               PhotoPolicyProperties policy) {
        this.profileRepository = profileRepository;
        this.photoStorage = photoStorage;
        this.policy = policy;
    }

    @Override
    public Profile addPhoto(ProfileId profileId, byte[] photoBytes, String contentType) {
        Profile profile = loadProfile(profileId);
        if (photoBytes == null || photoBytes.length == 0) {
            throw new IllegalArgumentException("Photo must not be empty");
        }
        String normalizedType = validate(photoBytes.length, contentType, profile.photoUrns().size());

        String urn = photoStorage.store(photoBytes, normalizedType);
        List<String> urns = new ArrayList<>(profile.photoUrns());
        urns.add(urn);
        try {
            return profileRepository.save(profile.withPhotoUrns(urns, Instant.now()));
        } catch (RuntimeException e) {
            // Avoid leaving an orphaned object in storage if the profile could not be updated.
            try {
                photoStorage.delete(urn);
            } catch (RuntimeException ignored) {
                // best effort; original failure is more relevant
            }
            throw e;
        }
    }

    @Override
    public Profile removePhoto(ProfileId profileId, String urn) {
        Profile profile = loadProfile(profileId);
        if (!profile.photoUrns().contains(urn)) {
            throw new PhotoNotFoundException(urn);
        }
        List<String> urns = new ArrayList<>(profile.photoUrns());
        urns.remove(urn);
        Profile saved = profileRepository.save(profile.withPhotoUrns(urns, Instant.now()));
        photoStorage.delete(urn);
        return saved;
    }

    @Override
    public byte[] getPhoto(ProfileId profileId, String urn) {
        Profile profile = loadProfile(profileId);
        if (!profile.photoUrns().contains(urn)) {
            throw new PhotoNotFoundException(urn);
        }
        return photoStorage.retrieve(urn);
    }

    private String validate(long sizeBytes, String contentType, int currentPhotoCount) {
        if (sizeBytes > policy.maxSizeBytes()) {
            throw new PhotoTooLargeException(sizeBytes, policy.maxSizeBytes());
        }
        String normalized = normalizeContentType(contentType);
        boolean allowed = policy.allowedContentTypes().stream()
                .anyMatch(t -> t.toLowerCase(Locale.ROOT).equals(normalized));
        if (!allowed) {
            throw new UnsupportedPhotoTypeException(
                    contentType == null ? "unknown" : contentType, policy.allowedContentTypes());
        }
        if (currentPhotoCount >= policy.maxPhotosPerProfile()) {
            throw new TooManyPhotosException(policy.maxPhotosPerProfile());
        }
        return normalized;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        String base = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private Profile loadProfile(ProfileId id) {
        return profileRepository.findById(id).orElseThrow(() -> new ProfileNotFoundException(id.value()));
    }
}
