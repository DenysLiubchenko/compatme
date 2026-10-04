package ua.kpi.project.compatme.adapter.in.web.dto;

import java.util.List;

/** Result of a successful photo upload: the new photo's URN and the profile's full photo list. */
public record PhotoUploadResponse(String urn, List<String> photoUrns) {
}
