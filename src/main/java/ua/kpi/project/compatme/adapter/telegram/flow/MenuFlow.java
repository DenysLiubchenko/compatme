package ua.kpi.project.compatme.adapter.telegram.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import ua.kpi.project.compatme.adapter.telegram.BackendApiClient;
import ua.kpi.project.compatme.adapter.telegram.state.ConversationStep;
import ua.kpi.project.compatme.adapter.telegram.ui.Card;
import ua.kpi.project.compatme.adapter.telegram.ui.Keyboards;
import ua.kpi.project.compatme.adapter.telegram.ui.MainMenuKeyboard;
import ua.kpi.project.compatme.adapter.telegram.ui.ProfileCardFormatter;
import ua.kpi.project.compatme.adapter.telegram.ui.TelegramSender;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Main menu (persistent bottom keyboard + inline menu), own-profile card and help text. */
public class MenuFlow {

    private static final Logger log = LoggerFactory.getLogger(MenuFlow.class);

    /** Steps in which the user is typing/choosing input for a form; menu labels must not be consumed as data. */
    private static final Set<ConversationStep> FORM_STEPS = EnumSet.of(
            ConversationStep.NAME, ConversationStep.AGE, ConversationStep.GENDER, ConversationStep.ORIENTATION,
            ConversationStep.SEEKING_GENDERS, ConversationStep.LOCATION_CHOICE,
            ConversationStep.LOCATION_SHARE_PENDING, ConversationStep.LOCATION_MANUAL_COUNTRY,
            ConversationStep.LOCATION_MANUAL_CITY, ConversationStep.LOCATION_CONFIRM,
            ConversationStep.SEARCH_SCOPE, ConversationStep.AGE_RANGE, ConversationStep.SELF_DESCRIPTION,
            ConversationStep.PREFERENCE_DESCRIPTION, ConversationStep.PHOTOS, ConversationStep.PHOTO_MANAGE,
            ConversationStep.REVIEW, ConversationStep.SETTINGS_AGE_RANGE);

    static final String HELP_TEXT = """
            ℹ️ How CompatMe works

            🔍 Browse — see people who match you and tap 👍 Like or 👎 Skip.
            👤 My Profile — view and edit your profile.
            ⚙️ Preferences — describe who you're looking for, or change search scope and age range.
            ❓ Help — this message.

            Commands: /menu opens the main menu, /settings opens settings.""";

    private final TelegramSender telegram;
    private final BackendApiClient backendApiClient;
    private final BrowsingFlow browsingFlow;

    public MenuFlow(TelegramSender telegram, BackendApiClient backendApiClient, BrowsingFlow browsingFlow) {
        this.telegram = telegram;
        this.backendApiClient = backendApiClient;
        this.browsingFlow = browsingFlow;
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

    /**
     * (Re)sends the persistent bottom keyboard, then the inline main menu. Used after profile
     * creation, {@code /menu} and {@code /start} for existing users.
     */
    public void sendMainMenu(long chatId, String telegramUserId) {
        browsingFlow.endSession(chatId, telegramUserId);
        telegram.send(SendMessage.builder().chatId(chatId)
                .text("👇 Use the menu below at any time.")
                .replyMarkup(MainMenuKeyboard.build())
                .build());
        telegram.sendNew(chatId, "What would you like to do?", Keyboards.mainMenu());
    }

    /** "⬅️ Back to Menu" on a card: the card itself turns into the inline main menu. */
    public void showMainMenuInPlace(long chatId, String telegramUserId, Integer messageId) {
        browsingFlow.clearBrowsing(telegramUserId);
        browsingFlow.showCard(chatId, telegramUserId, messageId,
                Card.text("What would you like to do?", Keyboards.mainMenu()));
    }

    /** Own profile shown in the single tracked card message ({@code sourceMessageId} may be {@code null}). */
    public void showOwnProfile(long chatId, String telegramUserId, Integer sourceMessageId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            browsingFlow.showCard(chatId, telegramUserId, sourceMessageId,
                    ProfileCardFormatter.toCard(profile, true, Keyboards.ownProfile()));
        } catch (Exception e) {
            log.warn("Failed to load own profile for chat {}: {}", chatId, e.getMessage());
            telegram.sendText(chatId, "We couldn't load your profile. Send /start to create one.");
        }
    }

    public void sendHelp(long chatId) {
        telegram.sendText(chatId, HELP_TEXT);
    }
}
