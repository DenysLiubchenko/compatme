package ua.kpi.project.compatme.application.dto;

/** Result of {@link ua.kpi.project.compatme.application.port.in.RecordLikeUseCase#recordLike}. */
public record RecordLikeResult(boolean mutualMatch) {
}
