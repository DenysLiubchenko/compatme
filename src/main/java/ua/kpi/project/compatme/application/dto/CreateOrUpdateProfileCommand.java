package ua.kpi.project.compatme.application.dto;

import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.LocationScope;
import ua.kpi.project.compatme.domain.model.Orientation;
import ua.kpi.project.compatme.domain.model.OptionalProfileFields;

import java.util.List;
import java.util.Set;

/**
 * Command for creating or updating a profile. Framework-agnostic — built by the web adapter from
 * the inbound request DTO, never a Spring/Jakarta-validated type itself.
 *
 * @param archetypeIds thesis-evaluation-only metadata (synthetic archetype tags); never read by
 *     scoring logic — see {@link ua.kpi.project.compatme.domain.model.Profile#archetypeIds()}.
 * @param country mandatory country, matched by the location-scope recommendation
 *     filter (see {@link ua.kpi.project.compatme.domain.model.LocationScope}).
 * @param city mandatory city, matched by the location-scope recommendation filter.
 * @param searchScope optional per-user default search scope; {@code null} keeps the existing value
 *     (or WORLDWIDE for new profiles).
 * @param photoUrls optional set of photo URLs/URNs; each must start with {@code http://} or 
 *     {@code https://} when present (enforced by the domain {@code Profile} constructor).
 */
public record CreateOrUpdateProfileCommand(
        String profileId,
        String telegramUserId,
        String displayName,
        Integer age,
        Gender gender,
        Orientation orientation,
        Set<Gender> seekingGenders,
        String selfDescription,
        String preferenceDescription,
        List<Integer> archetypeIds,
        String country,
        String city,
        List<String> photoUrls,
        OptionalProfileFields optionalFields,
        LocationScope searchScope) {

    /** Convenience constructor: no explicit default scope (new profiles get WORLDWIDE, updates keep theirs). */
    public CreateOrUpdateProfileCommand(
            String profileId, String telegramUserId, String displayName, Integer age, Gender gender,
            Orientation orientation, Set<Gender> seekingGenders, String selfDescription,
            String preferenceDescription, List<Integer> archetypeIds, String country, String city,
            List<String> photoUrls, OptionalProfileFields optionalFields) {
        this(profileId, telegramUserId, displayName, age, gender, orientation, seekingGenders, selfDescription,
                preferenceDescription, archetypeIds, country, city, photoUrls, optionalFields, null);
    }
}
