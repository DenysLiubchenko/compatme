package ua.kpi.project.compatme.application.port.in;

import ua.kpi.project.compatme.domain.model.ProfileId;

/**
 * Inbound use case for declining a like received from "Who Liked Me" (a "dislike", as opposed to
 * merely skipping past it). The rejected liker is removed from the list and is not notified; if
 * they like the profile again later, they appear again. Rejecting a like that does not exist is a no-op.
 */
public interface RejectLikeUseCase {

    void rejectLike(ProfileId profileId, ProfileId likerId);
}
