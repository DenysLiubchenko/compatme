package ua.kpi.project.compatme.application.port.in;

import ua.kpi.project.compatme.application.dto.RecordLikeResult;
import ua.kpi.project.compatme.domain.model.ProfileId;

/**
 * Inbound use case for recording a "like" while browsing matches or "Who Liked Me" — an explicit
 * user action, entirely independent of the computed compatibility score. Detects (and reports)
 * whether this like creates a mutual match, i.e. the liked profile had already liked the liker
 * back beforehand.
 */
public interface RecordLikeUseCase {

    RecordLikeResult recordLike(ProfileId likerId, ProfileId likedId);
}
