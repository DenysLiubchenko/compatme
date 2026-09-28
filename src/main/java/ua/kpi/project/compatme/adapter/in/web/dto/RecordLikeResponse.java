package ua.kpi.project.compatme.adapter.in.web.dto;

/** Response for {@code POST /api/v1/profiles/{likerId}/likes}. */
public record RecordLikeResponse(boolean mutualMatch) {
}
