package ua.kpi.project.compatme.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import ua.kpi.project.compatme.domain.model.Gender;

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
        List<Integer> archetypeIds,

        /** Optional free-text country, used only by the location-scope recommendation filter. */
        String country,

        /** Optional free-text city, used only by the location-scope recommendation filter. */
        String city,

        /**
         * Optional photo URL — only the URL is stored, never image bytes. Must start with
         * {@code http://} or {@code https://} when present (also enforced, authoritatively, by
         * the domain {@code Profile} constructor).
         */
        @Pattern(regexp = "^https?://.+", message = "photoUrl must start with http:// or https://")
        String photoUrl,

        /**
         * Telegram {@code file_id} references for up to 5 photos uploaded via the bot.
         * Presentation-only — never read by the compatibility scoring/recommendation logic. Only
         * the Telegram adapter ever populates this (Telegram hosts the actual image files).
         */
        List<String> photoFileIds) {
}
