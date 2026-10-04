package ua.kpi.project.compatme.adapter.telegram.validation;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Free-text input validation for the onboarding conversation flow (see
 * {@code adapter.telegram.ConversationFlowHandler}). Pure static functions — no Telegram or
 * Spring dependency — so validation rules can be unit-tested in isolation from the bot itself.
 */
public final class ProfileInputValidator {

    private static final int MAX_NAME_LENGTH = 50;
    private static final int MIN_AGE = 18;
    private static final int MAX_AGE = 99;
    private static final int MAX_LOCATION_LENGTH = 100;
    private static final int MIN_DESCRIPTION_LENGTH = 10;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_PHOTO_URL_LENGTH = 2048;

    private static final Pattern AGE_RANGE = Pattern.compile("^(\\d{1,3})\\s*[-\u2013\\s]\\s*(\\d{1,3})$");

    private ProfileInputValidator() {
    }

    /** Non-blank, at most {@value #MAX_NAME_LENGTH} characters after trimming. */
    public static boolean isValidName(String text) {
        String trimmed = text == null ? "" : text.trim();
        return !trimmed.isEmpty() && trimmed.length() <= MAX_NAME_LENGTH;
    }

    /**
     * Parses {@code text} as an integer age in {@code [18, 99]}. Returns an empty
     * {@link OptionalInt} for anything that doesn't parse as an integer or falls outside that
     * range — the caller re-prompts with the same message in either case, per the onboarding
     * spec ("please enter a number between 18 and 99").
     */
    public static OptionalInt parseValidAge(String text) {
        if (text == null) {
            return OptionalInt.empty();
        }
        try {
            int age = Integer.parseInt(text.trim());
            if (age < MIN_AGE || age > MAX_AGE) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(age);
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /**
     * Parses a preferred age range such as {@code 25-35} (also accepts an en dash or whitespace
     * as separator). Both bounds must be in {@code [18, 99]} and min must not exceed max.
     * Returns {@code {min, max}}, or empty when invalid.
     */
    public static Optional<int[]> parseAgeRange(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher matcher = AGE_RANGE.matcher(text.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int min = Integer.parseInt(matcher.group(1));
        int max = Integer.parseInt(matcher.group(2));
        if (min < MIN_AGE || max > MAX_AGE || min > max) {
            return Optional.empty();
        }
        return Optional.of(new int[] {min, max});
    }

    /** Non-blank, at most {@value #MAX_LOCATION_LENGTH} characters after trimming — used for manual country/city entry. */
    public static boolean isValidLocationText(String text) {
        String trimmed = text == null ? "" : text.trim();
        return !trimmed.isEmpty() && trimmed.length() <= MAX_LOCATION_LENGTH;
    }

    /**
     * Non-blank, at least {@value #MIN_DESCRIPTION_LENGTH} characters (so near-empty text like
     * "hi" doesn't slip through as a self/preference description) and at most
     * {@value #MAX_DESCRIPTION_LENGTH} characters, after trimming.
     */
    public static boolean isValidDescription(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.length() >= MIN_DESCRIPTION_LENGTH && trimmed.length() <= MAX_DESCRIPTION_LENGTH;
    }

    /** Validates a photo reference as a bounded HTTP(S) URL; the URL is stored but never fetched. */
    public static boolean isValidPhotoUrl(String text) {
        String value = text == null ? "" : text.trim();
        return value.length() <= MAX_PHOTO_URL_LENGTH && value.matches("(?i)^https?://\\S+$");
    }
}
