package ua.kpi.project.compatme.adapter.in.web.dto;

import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Orientation;
import ua.kpi.project.compatme.domain.model.OptionalProfileFields;

import java.time.Instant;
import java.util.Set;

/**
 * Outbound response DTO for a profile. Deliberately omits embedding vectors (large, internal
 * implementation detail) and only reports whether each embedding has been computed.
 *
 * <p>{@code telegramUserId} is never {@code null} in this response: profiles created without a
 * real Telegram account (e.g. from sample/evaluation datasets) have it mapped to a readable
 * placeholder string by {@link ua.kpi.project.compatme.adapter.in.web.ProfileWebMapper} instead
 * of being omitted, so API/Swagger consumers always see an explicit value.
 */
public record ProfileResponse(
        String id,
        String telegramUserId,
        String displayName,
        Integer age,
        Gender gender,
        Orientation orientation,
        String country,
        String city,
        ua.kpi.project.compatme.domain.model.LocationScope searchScope,
        Integer minPreferredAge,
        Integer maxPreferredAge,
        Set<Gender> seekingGenders,
        String selfDescription,
        String preferenceDescription,
        boolean hasSelfEmbedding,
        boolean hasPreferenceEmbedding,
        Instant createdAt,
        Instant updatedAt,

        /** Thesis-evaluation-only metadata: synthetic personality archetype tags. Purely
         * descriptive — never read by the compatibility scoring/recommendation logic.
         */
        java.util.List<Integer> archetypeIds,

        OptionalProfileFields optionalFields,

        /** Photo URNs identify objects in bucket storage; bytes are served by the photo endpoint. */
        java.util.List<String> photoUrns) {
}
