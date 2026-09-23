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
        List<Integer> archetypeIds) {
}
