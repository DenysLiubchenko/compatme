package ua.kpi.project.compatme.adapter.telegram.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import ua.kpi.project.compatme.adapter.telegram.BackendApiClient;
import ua.kpi.project.compatme.adapter.telegram.session.BrowsingSession;
import ua.kpi.project.compatme.adapter.telegram.session.BrowsingSessionStore;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStep;
import ua.kpi.project.compatme.adapter.telegram.ui.MainMenuKeyboard;
import ua.kpi.project.compatme.adapter.telegram.ui.ProfileCardFormatter;
import ua.kpi.project.compatme.adapter.telegram.ui.ReplyKeyboards;
import ua.kpi.project.compatme.adapter.telegram.ui.TelegramSender;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent bottom menu (a reply keyboard, sent once and then left alone), the "My Profile" and
 * "Preferences" sub-menus (which keep the four top-level buttons and add their own rows) and help.
 */
public class MenuFlow {

    private static final Logger log = LoggerFactory.getLogger(MenuFlow.class);
    private static final int CAPTION_LIMIT = 1024;

    /** Steps in which the user is typing/choosing input for a form; menu labels must not be consumed as data. */
    private static final Set<ConversationStep> FORM_STEPS = EnumSet.of(
            ConversationStep.NAME, ConversationStep.AGE, ConversationStep.GENDER, ConversationStep.ORIENTATION,
            ConversationStep.SEEKING_GENDERS, ConversationStep.LOCATION_CHOICE,
            ConversationStep.LOCATION_SHARE_PENDING, ConversationStep.LOCATION_MANUAL_COUNTRY,
            ConversationStep.LOCATION_MANUAL_CITY, ConversationStep.LOCATION_CONFIRM,
            ConversationStep.SEARCH_SCOPE, ConversationStep.AGE_RANGE, ConversationStep.SELF_DESCRIPTION,
            ConversationStep.PREFERENCE_DESCRIPTION, ConversationStep.PHOTOS, ConversationStep.PHOTO_MANAGE,
            ConversationStep.REVIEW, ConversationStep.SETTINGS_AGE_RANGE, ConversationStep.SETTINGS_SCOPE,
            ConversationStep.DELETE_CONFIRM);

    static final String HELP_TEXT = """
            ℹ️ How CompatMe works

            🔍 Browse — see people who match you and tap 👍 Like or 👎 Skip on their card.
            👤 My Profile — view your profile, edit it, see who liked you, pause or delete your account.
            ⚙️ Preferences — describe who you're looking for, or change search scope and age range.
            ❓ Help — this message.

            The menu at the bottom of the chat is always available. /menu re-opens it.""";

    private final TelegramSender telegram;
    private final BackendApiClient backendApiClient;
    private final BrowsingFlow browsingFlow;
    private final BrowsingSessionStore sessions;

    /** Users who have been sent the persistent keyboard during this bot run. */
    private final Set<String> menuAttached = ConcurrentHashMap.newKeySet();

    public MenuFlow(
            TelegramSender telegram,
            BackendApiClient backendApiClient,
            BrowsingFlow browsingFlow,
            BrowsingSessionStore sessions) {
        this.telegram = telegram;
        this.backendApiClient = backendApiClient;
        this.browsingFlow = browsingFlow;
        this.sessions = sessions;
    }

    public static boolean isInFormStep(ConversationStep step) {
        return FORM_STEPS.contains(step);
    }

    public boolean hasExistingProfile(String telegramUserId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            return profile != null && profile.get("id") != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** The user pressed a bottom-menu button, so they evidently have the keyboard. */
    public void markMenuAttached(String telegramUserId) {
        menuAttached.add(telegramUserId);
    }

    /**
     * Sends the persistent keyboard ONCE per bot run to an onboarded user who has not been given it
     * yet (e.g. a user from before this keyboard existed). Never interrupts a form in progress.
     */
    public void ensureMenuAttached(long chatId, String telegramUserId, ConversationStep step) {
        if (menuAttached.contains(telegramUserId) || isInFormStep(step)) {
            return;
        }
        if (!hasExistingProfile(telegramUserId)) {
            return;
        }
        sendMenuKeyboard(chatId, telegramUserId, "👇 Use the menu below at any time.");
    }

    /** (Re)sends the persistent bottom keyboard. Used after profile creation, {@code /menu} and {@code /start}. */
    public void sendMainMenu(long chatId, String telegramUserId) {
        browsingFlow.endSession(chatId, telegramUserId);
        sendMenuKeyboard(chatId, telegramUserId, "👇 Use the menu below at any time.");
    }

    private void sendMenuKeyboard(long chatId, String telegramUserId, String text) {
        telegram.sendNew(chatId, text, MainMenuKeyboard.build());
        menuAttached.add(telegramUserId);
    }

    /**
     * Own profile with the "My Profile" menu (top-level buttons + edit / who liked me / pause /
     * delete). The previous profile message is deleted first so repeated taps never stack.
     */
    public void showOwnProfile(long chatId, String telegramUserId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            BrowsingSession session = sessions.getOrCreate(telegramUserId);
            telegram.deleteQuietly(chatId, session.profileMessageId());

            String text = ProfileCardFormatter.formatProfileCard(profile, true);
            String photoUrl = ProfileCardFormatter.firstPhotoUrl(profile);
            Integer sent = null;
            if (photoUrl != null) {
                sent = telegram.sendPhoto(SendPhoto.builder().chatId(chatId)
                        .photo(new InputFile(photoUrl))
                        .caption(truncate(text))
                        .replyMarkup(ReplyKeyboards.profileMenu())
                        .build());
            }
            if (sent == null) {
                sent = telegram.sendMessage(SendMessage.builder().chatId(chatId).text(text)
                        .replyMarkup(ReplyKeyboards.profileMenu())
                        .build());
            }
            session.setProfileMessageId(sent);
            menuAttached.add(telegramUserId);
        } catch (Exception e) {
            log.warn("Failed to load own profile for chat {}: {}", chatId, e.getMessage());
            telegram.sendText(chatId, "We couldn't load your profile. Send /start to create one.");
        }
    }

    /** Shows the Preferences menu (top-level buttons + preference actions) under {@code text}. */
    public void showPreferencesMenu(long chatId, String telegramUserId, String text) {
        telegram.sendNew(chatId, text, ReplyKeyboards.preferencesMenu());
        menuAttached.add(telegramUserId);
    }

    public void sendHelp(long chatId) {
        telegram.sendText(chatId, HELP_TEXT);
    }

    private static String truncate(String text) {
        return text.length() <= CAPTION_LIMIT ? text : text.substring(0, CAPTION_LIMIT - 1) + "…";
    }
}
