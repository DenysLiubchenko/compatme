package ua.kpi.project.compatme.application.port.out;

import ua.kpi.project.compatme.domain.model.Profile;

/** Sends notifications related to likes and mutual matches. */
public interface LikeNotificationPort {

    void notifyNewLike(Profile recipient, Profile liker);

    void notifyMutualMatch(Profile first, Profile second);
}
