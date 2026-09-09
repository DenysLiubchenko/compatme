package ua.kpi.project.compatme.adapter.in.web.dto;

import ua.kpi.project.compatme.domain.model.Gender;

import java.time.Instant;
import java.util.Set;

/**
 * Outbound response DTO for a profile. Deliberately omits embedding vectors (large, internal
 * implementation detail) and only reports whether each embedding has been computed.
 */
public record ProfileResponse(
        String id,
        String telegramUserId,
        String displayName,
        Integer age,
        Gender gender,
        Set<Gender> seekingGenders,
        String selfDescription,
        String preferenceDescription,
        boolean hasSelfEmbedding,
        boolean hasPreferenceEmbedding,
        Instant createdAt,
        Instant updatedAt) {
}
