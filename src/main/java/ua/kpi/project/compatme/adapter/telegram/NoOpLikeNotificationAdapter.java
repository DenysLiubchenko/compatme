package ua.kpi.project.compatme.adapter.telegram;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.application.port.out.LikeNotificationPort;
import ua.kpi.project.compatme.domain.model.Profile;

/** Keeps like recording available when Telegram notifications are disabled. */
@Component
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpLikeNotificationAdapter implements LikeNotificationPort {

    @Override
    public void notifyNewLike(Profile recipient, Profile liker) {
        // Notifications are disabled.
    }

    @Override
    public void notifyMutualMatch(Profile first, Profile second) {
        // Notifications are disabled.
    }
}
