package ua.kpi.project.compatme.application.port.in;

import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

/** Inbound port for managing a profile's stored photos. Shared by the REST and Telegram paths. */
public interface ProfilePhotoUseCase {

    /**
     * Validates (size, type, count) and stores the photo, attaching its URN to the profile.
     *
     * @return the updated profile; the new URN is the last element of {@code photoUrns()}
     */
    Profile addPhoto(ProfileId profileId, byte[] photoBytes, String contentType);

    Profile removePhoto(ProfileId profileId, String urn);

    byte[] getPhoto(ProfileId profileId, String urn);
}
