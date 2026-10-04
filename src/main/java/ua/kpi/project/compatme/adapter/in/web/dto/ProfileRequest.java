package ua.kpi.project.compatme.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Orientation;
import ua.kpi.project.compatme.domain.model.OptionalProfileFields;

import java.util.List;
import java.util.Set;

/**
 * Inbound request DTO for creating/updating a profile. Bean Validation annotations enforce basic
 * invariants at the HTTP boundary before the request ever reaches the application layer.
 *
 * <p>{@code telegramUserId} is optional: it is always present for profiles created through the
 * Telegram bot (which supplies the real chat id), but is expected to be {@code null} for profiles
 * created from sample/evaluation datasets rather than a real Telegram account.
 */
public record ProfileRequest(
        String telegramUserId,

        @NotBlank(message = "displayName must not be blank")
        String displayName,

        @Min(value = 18, message = "age must be at least 18")
        @Max(value = 120, message = "age must be at most 120")
        Integer age,

        @jakarta.validation.constraints.NotNull(message = "sex is required")
        Gender gender,

        @jakarta.validation.constraints.NotNull(message = "orientation is required")
        Orientation orientation,

        @NotBlank(message = "country must not be blank")
        String country,

        @NotBlank(message = "city must not be blank")
        String city,

        /** Optional default search scope (CITY, COUNTRY, WORLDWIDE); omitted keeps the current one. */
        ua.kpi.project.compatme.domain.model.LocationScope searchScope,

        /** Optional preferred match age range; must be provided together. */
        @Min(value = 18, message = "minPreferredAge must be at least 18")
        @Max(value = 120, message = "minPreferredAge must be at most 120")
        Integer minPreferredAge,

        @Min(value = 18, message = "maxPreferredAge must be at least 18")
        @Max(value = 120, message = "maxPreferredAge must be at most 120")
        Integer maxPreferredAge,

        Set<Gender> seekingGenders,

        @NotBlank(message = "selfDescription must not be blank")
        String selfDescription,

        @NotBlank(message = "preferenceDescription must not be blank")
        String preferenceDescription,

        /**
         * Optional, thesis-evaluation-only metadata: synthetic personality archetype tags for this
         * profile. Purely descriptive — never read by the compatibility scoring/recommendation
         * logic. Provided so evaluation datasets can carry archetype labels through the same API
         * used for real profiles.
         */
        List<Integer> archetypeIds,

        /** Optional structured attributes; unspecified fields remain empty. */
        OptionalProfileFields optionalFields,

        /** Explicit URN/key references only; never fetched, analyzed, or processed. */
        List<String> photoUrns) {
}
