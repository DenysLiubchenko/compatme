package ua.kpi.project.compatme.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import ua.kpi.project.compatme.domain.model.Gender;

import java.util.List;
import java.util.Set;

/**
 * Inbound request DTO for creating/updating a profile. Bean Validation annotations enforce basic
 * invariants at the HTTP boundary before the request ever reaches the application layer.
 */
public record ProfileRequest(
        String telegramUserId,

        @NotBlank(message = "displayName must not be blank")
        String displayName,

        @Min(value = 18, message = "age must be at least 18")
        @Max(value = 120, message = "age must be at most 120")
        Integer age,

        Gender gender,

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
        List<Integer> archetypeIds) {
}
