package ua.kpi.project.compatme.application.dto;

import ua.kpi.project.compatme.domain.model.Gender;

import java.util.List;
import java.util.Set;

/**
 * Command for creating or updating a profile. Framework-agnostic — built by the web adapter from
 * the inbound request DTO, never a Spring/Jakarta-validated type itself.
 *
 * @param archetypeIds thesis-evaluation-only metadata (synthetic archetype tags); never read by
 *     scoring logic — see {@link ua.kpi.project.compatme.domain.model.Profile#archetypeIds()}.
 * @param country optional free-text country, used only by the location-scope recommendation
 *     filter (see {@link ua.kpi.project.compatme.domain.model.LocationScope}).
 * @param city optional free-text city, used only by the location-scope recommendation filter.
 * @param photoUrl optional photo URL; must start with {@code http://} or {@code https://} when
 *     present (enforced by the domain {@code Profile} constructor).
 * @param photoFileIds Telegram {@code file_id} references for up to 5 uploaded photos;
 *     presentation-only, never read by scoring logic — see
 *     {@link ua.kpi.project.compatme.domain.model.Profile#photoFileIds()}.
 */
public record CreateOrUpdateProfileCommand(
        String profileId,
        String telegramUserId,
        String displayName,
        Integer age,
        Gender gender,
        Set<Gender> seekingGenders,
        String selfDescription,
        String preferenceDescription,
        List<Integer> archetypeIds,
        String country,
        String city,
        String photoUrl,
        List<String> photoFileIds) {
}
