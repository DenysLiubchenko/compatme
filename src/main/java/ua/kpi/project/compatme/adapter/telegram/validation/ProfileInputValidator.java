package ua.kpi.project.compatme.adapter.telegram.validation;

import java.util.OptionalInt;

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
}
