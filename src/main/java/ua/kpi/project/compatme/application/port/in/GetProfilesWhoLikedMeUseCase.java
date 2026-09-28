package ua.kpi.project.compatme.application.port.in;

import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.util.List;

/** Inbound use case for the "Who Liked Me" view: everyone who has liked the given profile, most recent first. */
public interface GetProfilesWhoLikedMeUseCase {

    List<Profile> getProfilesWhoLikedMe(ProfileId profileId);
}
