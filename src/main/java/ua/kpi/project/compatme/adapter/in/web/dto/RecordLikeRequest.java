package ua.kpi.project.compatme.adapter.in.web.dto;

/** Body for {@code POST /api/v1/profiles/{likerId}/likes}. */
public record RecordLikeRequest(String likedProfileId) {
}
