package ua.kpi.project.compatme.adapter.telegram.ui;

import ua.kpi.project.compatme.adapter.telegram.state.ConversationStep;

import java.util.Optional;

import static ua.kpi.project.compatme.adapter.telegram.ui.ReplyKeyboards.*;

/**
 * Maps the plain-text label of a pressed reply-keyboard button to the internal action id used by
 * the flows (the same ids the old inline buttons carried as callback data). Resolution depends on
 * the user's current step, so a label such as "Male" only counts as a button press while the
 * matching keyboard is actually the active one, and never swallows genuine free-text input.
 */
public final class ReplyButtons {

    private ReplyButtons() {
    }

    public static Optional<String> resolve(String rawLabel, ConversationStep step) {
        if (rawLabel == null) {
            return Optional.empty();
        }
        String label = rawLabel.trim();
        return switch (step) {
            case WELCOME -> eq(label, CREATE_PROFILE, "start_create").or(() -> idle(label));
            case GENDER -> gender(label).or(() -> eq(label, BACK, "back"));
            case ORIENTATION -> orientation(label).or(() -> eq(label, BACK, "back"));
            case SEEKING_GENDERS -> seeking(label).or(() -> eq(label, CONTINUE, "seek:continue"))
                    .or(() -> eq(label, BACK, "back"));
            case LOCATION_CHOICE, LOCATION_SHARE_PENDING -> eq(label, ENTER_MANUALLY, "loc:manual")
                    .or(() -> eq(label, BACK, "back"));
            case LOCATION_CONFIRM -> eq(label, CONFIRM_YES, "loc:confirm_yes")
                    .or(() -> eq(label, CONFIRM_NO, "loc:confirm_no"))
                    .or(() -> eq(label, BACK, "back"));
            case SEARCH_SCOPE -> scope(label, "scope:");
            case SETTINGS_SCOPE -> scope(label, "setscope:").or(() -> eq(label, BACK, "back"));
            case AGE_RANGE -> label.startsWith(AGE_DEFAULT_PREFIX) ? Optional.of("agerange:default") : Optional.empty();
            case SETTINGS_AGE_RANGE -> eq(label, BACK, "back");
            case PHOTOS -> eq(label, ADD_PHOTO, "photo:add")
                    .or(() -> eq(label, ADD_ANOTHER, "photo:add_another"))
                    .or(() -> eq(label, SKIP_PHOTOS, "photo:skip"))
                    .or(() -> eq(label, DONE_PHOTOS, "photo:done"));
            case PHOTO_MANAGE -> photoManage(label);
            case REVIEW -> review(label);
            case DELETE_CONFIRM -> eq(label, DELETE_YES, "delete:yes").or(() -> eq(label, DELETE_NO, "delete:no"));
            case DONE, SETTINGS_MENU -> idle(label);
            default -> Optional.empty();
        };
    }

    private static Optional<String> eq(String label, String expected, String action) {
        return label.equals(expected) ? Optional.of(action) : Optional.empty();
    }

    /** Buttons of the "My Profile" / "Preferences" menus, valid whenever no form is in progress. */
    private static Optional<String> idle(String label) {
        return switch (label) {
            case EDIT_PROFILE -> Optional.of("profile:edit");
            case WHO_LIKED_ME -> Optional.of("menu:liked");
            case PAUSE -> Optional.of("settings:pause");
            case DELETE_ACCOUNT -> Optional.of("settings:delete");
            case DESCRIBE_PREFERENCES -> Optional.of("post:prefs");
            case SEARCH_SCOPE -> Optional.of("settings:scope");
            case MATCH_AGE_RANGE -> Optional.of("settings:agerange");
            default -> Optional.empty();
        };
    }

    private static Optional<String> gender(String label) {
        return switch (label) {
            case MALE -> Optional.of("gender:MALE");
            case FEMALE -> Optional.of("gender:FEMALE");
            case NON_BINARY -> Optional.of("gender:NON_BINARY");
            default -> Optional.empty();
        };
    }

    private static Optional<String> seeking(String label) {
        String bare = label.startsWith(CHECKED_PREFIX) ? label.substring(CHECKED_PREFIX.length()) : label;
        return switch (bare) {
            case MALE -> Optional.of("seek:MALE");
            case FEMALE -> Optional.of("seek:FEMALE");
            case NON_BINARY -> Optional.of("seek:NON_BINARY");
            default -> Optional.empty();
        };
    }

    private static Optional<String> orientation(String label) {
        return switch (label) {
            case STRAIGHT -> Optional.of("orientation:STRAIGHT");
            case GAY -> Optional.of("orientation:GAY");
            case BISEXUAL -> Optional.of("orientation:BISEXUAL");
            case OTHER -> Optional.of("orientation:OTHER");
            default -> Optional.empty();
        };
    }

    private static Optional<String> scope(String label, String prefix) {
        return switch (label) {
            case SCOPE_CITY -> Optional.of(prefix + "CITY");
            case SCOPE_COUNTRY -> Optional.of(prefix + "COUNTRY");
            case SCOPE_WORLDWIDE -> Optional.of(prefix + "WORLDWIDE");
            default -> Optional.empty();
        };
    }

    private static Optional<String> photoManage(String label) {
        if (label.startsWith(REMOVE_URL_PREFIX)) {
            try {
                int number = Integer.parseInt(label.substring(REMOVE_URL_PREFIX.length()).trim());
                return Optional.of("photo_manage:remove:" + (number - 1));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return eq(label, MANAGE_ADD, "photo_manage:add").or(() -> eq(label, MANAGE_DONE, "photo_manage:done"));
    }

    private static Optional<String> review(String label) {
        return switch (label) {
            case REVIEW_SAVE -> Optional.of("review:save");
            case EDIT_NAME -> Optional.of("review:edit_name");
            case EDIT_AGE -> Optional.of("review:edit_age");
            case EDIT_SEX -> Optional.of("review:edit_gender");
            case EDIT_ORIENTATION -> Optional.of("review:edit_orientation");
            case EDIT_LOCATION -> Optional.of("review:edit_location");
            case EDIT_DESCRIPTIONS -> Optional.of("review:edit_desc");
            case EDIT_PHOTOS -> Optional.of("review:edit_photos");
            case EDIT_SCOPE -> Optional.of("review:edit_scope");
            case EDIT_AGE_RANGE -> Optional.of("review:edit_agerange");
            case BACK -> Optional.of("back");
            default -> Optional.empty();
        };
    }
}
