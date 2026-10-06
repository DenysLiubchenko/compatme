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
 * @param minPreferredAge optional lower bound of the preferred match age; must be paired with
 *     {@code maxPreferredAge}. When both are {@code null} the existing range is kept.
 * @param maxPreferredAge optional upper bound of the preferred match age.
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
        OptionalProfileFields optionalFields,
        LocationScope searchScope,
        Integer minPreferredAge,
        Integer maxPreferredAge) {

    /** Convenience constructor: no explicit default scope (new profiles get WORLDWIDE, updates keep theirs). */
    public CreateOrUpdateProfileCommand(
            String profileId, String telegramUserId, String displayName, Integer age, Gender gender,
            Orientation orientation, Set<Gender> seekingGenders, String selfDescription,
            String preferenceDescription, List<Integer> archetypeIds, String country, String city,
            OptionalProfileFields optionalFields) {
        this(profileId, telegramUserId, displayName, age, gender, orientation, seekingGenders, selfDescription,
                preferenceDescription, archetypeIds, country, city, optionalFields, null, null, null);
    }

    /** Convenience constructor without a preferred age range (keeps the existing one on update). */
    public CreateOrUpdateProfileCommand(
            String profileId, String telegramUserId, String displayName, Integer age, Gender gender,
            Orientation orientation, Set<Gender> seekingGenders, String selfDescription,
            String preferenceDescription, List<Integer> archetypeIds, String country, String city,
            OptionalProfileFields optionalFields, LocationScope searchScope) {
        this(profileId, telegramUserId, displayName, age, gender, orientation, seekingGenders, selfDescription,
                preferenceDescription, archetypeIds, country, city, optionalFields, searchScope, null, null);
    }
}
