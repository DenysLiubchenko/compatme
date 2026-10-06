package ua.kpi.project.compatme.adapter.telegram.ui;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Every non-card keyboard of the bot, as {@link ReplyKeyboardMarkup}s. Pressing a button sends its
 * label as plain text; {@link ReplyButtons} maps those labels back to actions.
 */
public final class ReplyKeyboards {

    public static final String BACK = "⬅️ Back";

    public static final String CREATE_PROFILE = "🚀 Create My Profile";

    public static final String MALE = "Male";
    public static final String FEMALE = "Female";
    public static final String NON_BINARY = "Non-binary";

    public static final String STRAIGHT = "Straight";
    public static final String GAY = "Gay";
    public static final String BISEXUAL = "Bisexual";
    public static final String OTHER = "Other";

    public static final String CHECKED_PREFIX = "✅ ";
    public static final String CONTINUE = "➡️ Continue";

    public static final String SHARE_LOCATION = "📍 Share My Location";
    public static final String ENTER_MANUALLY = "✍️ Enter Manually";
    public static final String CONFIRM_YES = "✅ Yes";
    public static final String CONFIRM_NO = "✍️ No, enter manually";

    public static final String ADD_PHOTO = "📷 Add Photo";
    public static final String SKIP_PHOTOS = "⏭ Skip for now";
    public static final String ADD_ANOTHER = "➕ Add Another";
    public static final String DONE_PHOTOS = "✅ Done Adding Photos";
    public static final String MANAGE_ADD = "➕ Add Photo";
    public static final String MANAGE_DONE = "✅ Done";
    public static final String REMOVE_URL_PREFIX = "🗑 Remove Photo ";

    public static final String SCOPE_CITY = "🏙 My city";
    public static final String SCOPE_COUNTRY = "🌍 My country";
    public static final String SCOPE_WORLDWIDE = "🌐 Worldwide";

    public static final String AGE_DEFAULT_PREFIX = "Use my age \u00b13";

    public static final String REVIEW_SAVE = "✅ Looks good, save it!";
    public static final String EDIT_NAME = "✏️ Edit Name";
    public static final String EDIT_AGE = "✏️ Edit Age";
    public static final String EDIT_SEX = "✏️ Edit Sex";
    public static final String EDIT_ORIENTATION = "✏️ Edit Orientation";
    public static final String EDIT_LOCATION = "✏️ Edit Location";
    public static final String EDIT_DESCRIPTIONS = "✏️ Edit Descriptions";
    public static final String EDIT_PHOTOS = "✏️ Edit Photos";
    public static final String EDIT_SCOPE = "✏️ Edit Search Scope";
    public static final String EDIT_AGE_RANGE = "✏️ Edit Age Range";

    // "My Profile" menu (its own view; replaces the main menu until "Main Menu" is pressed)
    public static final String EDIT_PROFILE = "✏️ Edit Profile";
    public static final String PAUSE = "🔕 Pause Matching";
    public static final String DELETE_ACCOUNT = "🗑 Delete My Account";
    public static final String MAIN_MENU = "⬅️ Main Menu";

    public static final String DELETE_YES = "❌ Yes, delete everything";
    public static final String DELETE_NO = "⬅️ No, keep my profile";

    private ReplyKeyboards() {
    }

    private static ReplyKeyboardMarkup of(List<List<String>> rows) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        markup.setSelective(false);
        List<KeyboardRow> keyboard = new ArrayList<>();
        for (List<String> labels : rows) {
            KeyboardRow row = new KeyboardRow();
            labels.forEach(label -> row.add(new KeyboardButton(label)));
            keyboard.add(row);
        }
        markup.setKeyboard(keyboard);
        return markup;
    }

    public static ReplyKeyboardMarkup welcome() {
        return of(List.of(List.of(CREATE_PROFILE)));
    }

    public static ReplyKeyboardMarkup gender() {
        return of(List.of(List.of(MALE, FEMALE, NON_BINARY), List.of(BACK)));
    }

    public static ReplyKeyboardMarkup orientation() {
        return of(List.of(List.of(STRAIGHT, GAY), List.of(BISEXUAL, OTHER), List.of(BACK)));
    }

    /** Multi-select: selected options carry a leading check mark in their label. */
    public static ReplyKeyboardMarkup seekingGenders(Set<String> selected) {
        return of(List.of(
                List.of(seekLabel(selected, "MALE", MALE), seekLabel(selected, "FEMALE", FEMALE),
                        seekLabel(selected, "NON_BINARY", NON_BINARY)),
                List.of(CONTINUE),
                List.of(BACK)));
    }

    private static String seekLabel(Set<String> selected, String value, String label) {
        return (selected.contains(value) ? CHECKED_PREFIX : "") + label;
    }

    /** The share button is a real {@code request_location} button, so no extra tap is needed. */
    public static ReplyKeyboardMarkup locationChoice() {
        ReplyKeyboardMarkup markup = of(List.of(List.of(ENTER_MANUALLY), List.of(BACK)));
        KeyboardRow share = new KeyboardRow();
        share.add(KeyboardButton.builder().text(SHARE_LOCATION).requestLocation(true).build());
        markup.getKeyboard().add(0, share);
        return markup;
    }

    public static ReplyKeyboardMarkup locationConfirm() {
        return of(List.of(List.of(CONFIRM_YES, CONFIRM_NO), List.of(BACK)));
    }

    public static ReplyKeyboardMarkup photos(int count, int max) {
        if (count == 0) {
            return of(List.of(List.of(ADD_PHOTO), List.of(SKIP_PHOTOS)));
        }
        if (count < max) {
            return of(List.of(List.of(ADD_ANOTHER, DONE_PHOTOS)));
        }
        return of(List.of(List.of(DONE_PHOTOS)));
    }

    public static ReplyKeyboardMarkup photoManage(int count, int max) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(List.of(REMOVE_URL_PREFIX + (i + 1)));
        }
        rows.add(count < max ? List.of(MANAGE_ADD, MANAGE_DONE) : List.of(MANAGE_DONE));
        return of(rows);
    }

    public static ReplyKeyboardMarkup scope() {
        return of(List.of(List.of(SCOPE_CITY), List.of(SCOPE_COUNTRY), List.of(SCOPE_WORLDWIDE)));
    }

    public static ReplyKeyboardMarkup scopeWithBack() {
        return of(List.of(List.of(SCOPE_CITY), List.of(SCOPE_COUNTRY), List.of(SCOPE_WORLDWIDE), List.of(BACK)));
    }

    public static String ageRangeDefaultLabel(int min, int max) {
        return AGE_DEFAULT_PREFIX + " (" + min + "-" + max + ")";
    }

    public static ReplyKeyboardMarkup ageRange(int min, int max) {
        return of(List.of(List.of(ageRangeDefaultLabel(min, max))));
    }

    public static ReplyKeyboardMarkup backOnly() {
        return of(List.of(List.of(BACK)));
    }

    public static ReplyKeyboardMarkup review() {
        return of(List.of(
                List.of(REVIEW_SAVE),
                List.of(EDIT_NAME, EDIT_AGE),
                List.of(EDIT_SEX, EDIT_ORIENTATION),
                List.of(EDIT_LOCATION, EDIT_DESCRIPTIONS),
                List.of(EDIT_PHOTOS, EDIT_SCOPE),
                List.of(EDIT_AGE_RANGE),
                List.of(BACK)));
    }

    public static ReplyKeyboardMarkup deleteConfirm() {
        return of(List.of(List.of(DELETE_YES), List.of(DELETE_NO)));
    }

    /** The profile view: only profile actions plus a way back to the main menu. */
    public static ReplyKeyboardMarkup profileMenu() {
        return of(List.of(
                List.of(EDIT_PROFILE),
                List.of(PAUSE, DELETE_ACCOUNT),
                List.of(MAIN_MENU)));
    }
}
