package ua.kpi.project.compatme.adapter.telegram.state;

/**
 * Steps of the button-driven onboarding/profile-creation conversation (see
 * {@code adapter.telegram.ConversationFlowHandler}), plus the always-accessible settings/deletion
 * sub-flow. Persisted per Telegram user in {@link ConversationStateStore} so an in-progress
 * conversation survives a bot restart during development.
 */
public enum ConversationStep {
    WELCOME,
    NAME,
    AGE,
    GENDER,
    ORIENTATION,
    SEEKING_GENDERS,
    LOCATION_CHOICE,
    LOCATION_SHARE_PENDING,
    LOCATION_MANUAL_COUNTRY,
    LOCATION_MANUAL_CITY,
    LOCATION_CONFIRM,

    /** Choose the default search scope (CITY / COUNTRY / WORLDWIDE), right after location. */
    SEARCH_SCOPE,
    SELF_DESCRIPTION,
    PREFERENCE_DESCRIPTION,

    /** "Add up to 5 photos" step, between PREFERENCE_DESCRIPTION and REVIEW. */
    PHOTOS,

    /** Review's "✏️ Edit Photos" sub-view: shows current photos + remove/add-more buttons. */
    PHOTO_MANAGE,

    REVIEW,
    DONE,

    /** Entered via {@code /settings}, independent of the onboarding step order above. */
    SETTINGS_MENU,

    /** "Are you sure?" step before {@code deleteProfile} is actually called. */
    DELETE_CONFIRM
}
