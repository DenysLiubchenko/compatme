package ua.kpi.project.compatme.adapter.telegram.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.kpi.project.compatme.adapter.telegram.BackendApiClient;
import ua.kpi.project.compatme.adapter.telegram.session.BrowsingSession;
import ua.kpi.project.compatme.adapter.telegram.session.BrowsingSessionStore;
import ua.kpi.project.compatme.adapter.telegram.ui.Card;
import ua.kpi.project.compatme.adapter.telegram.ui.CardMessenger;
import ua.kpi.project.compatme.adapter.telegram.ui.Keyboards;
import ua.kpi.project.compatme.adapter.telegram.ui.ProfileCardFormatter;
import ua.kpi.project.compatme.adapter.telegram.ui.TelegramSender;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

/**
 * "Browse" / "Who Liked Me": walks a queue of candidates while keeping exactly one card message
 * per user, edited in place on Like/Skip instead of sending a new message each time.
 */
public class BrowsingFlow {

    private static final Logger log = LoggerFactory.getLogger(BrowsingFlow.class);
    private static final int BROWSE_FETCH_SIZE = 20;

    private final TelegramSender telegram;
    private final BackendApiClient backendApiClient;
    private final BrowsingSessionStore sessions;
    private final CardMessenger cards;

    public BrowsingFlow(
            TelegramSender telegram,
            BackendApiClient backendApiClient,
            BrowsingSessionStore sessions,
            CardMessenger cards) {
        this.telegram = telegram;
        this.backendApiClient = backendApiClient;
        this.sessions = sessions;
        this.cards = cards;
    }

    /** @return {@code true} if {@code data} belonged to the browsing flow. */
    public boolean onCallback(long chatId, String telegramUserId, Integer messageId, String callbackQueryId, String data) {
        switch (data) {
            case "menu:matches" -> { telegram.ackSilently(callbackQueryId); startMatches(chatId, telegramUserId, messageId); }
            case "menu:liked" -> { telegram.ackSilently(callbackQueryId); startLikedMe(chatId, telegramUserId, messageId); }
            case "browse:like", "browse:likeback" -> { telegram.ackSilently(callbackQueryId); onLike(chatId, telegramUserId, messageId); }
            case "browse:skip", "browse:next" -> { telegram.ackSilently(callbackQueryId); onSkip(chatId, telegramUserId, messageId); }
            default -> { return false; }
        }
        return true;
    }

    /** Persistent "🔍 Browse": resume the current candidate in place, or start a fresh session. */
    public void resumeOrStartMatches(long chatId, String telegramUserId) {
        BrowsingSession session = sessions.getOrCreate(telegramUserId);
        Map<String, Object> current = session.current();
        if (current != null && BrowsingSession.MODE_MATCHES.equals(session.mode())) {
            showCard(chatId, telegramUserId, null,
                    ProfileCardFormatter.toCard(current, false, Keyboards.browseMatches()));
            return;
        }
        startMatches(chatId, telegramUserId, null);
    }

    public void startMatches(long chatId, String telegramUserId, Integer sourceMessageId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            String profileId = String.valueOf(profile.get("id"));
            List<Map<String, Object>> recommendations =
                    backendApiClient.getRecommendations(profileId, BROWSE_FETCH_SIZE);
            recommendations.forEach(candidate -> candidate.putIfAbsent("id", candidate.get("candidateId")));
            sessions.getOrCreate(telegramUserId)
                    .startBrowsing(BrowsingSession.MODE_MATCHES, new ArrayDeque<>(recommendations));
            showNext(chatId, telegramUserId, sourceMessageId);
        } catch (Exception e) {
            log.warn("Failed to fetch matches for chat {}: {}", chatId, e.getMessage());
            showCard(chatId, telegramUserId, sourceMessageId,
                    Card.text("Couldn't fetch matches right now. Try again shortly.", Keyboards.backToMenu()));
        }
    }

    public void startLikedMe(long chatId, String telegramUserId, Integer sourceMessageId) {
        try {
            Map<String, Object> profile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
            String profileId = String.valueOf(profile.get("id"));
            List<Map<String, Object>> likers = backendApiClient.getProfilesWhoLikedMe(profileId);
            if (likers.isEmpty()) {
                sessions.getOrCreate(telegramUserId).clearBrowsing();
                showCard(chatId, telegramUserId, sourceMessageId,
                        Card.text("No one yet — but check back soon!", Keyboards.backToMenu()));
                return;
            }
            sessions.getOrCreate(telegramUserId)
                    .startBrowsing(BrowsingSession.MODE_LIKED_ME, new ArrayDeque<>(likers));
            showNext(chatId, telegramUserId, sourceMessageId);
        } catch (Exception e) {
            log.warn("Failed to fetch who-liked-me for chat {}: {}", chatId, e.getMessage());
            showCard(chatId, telegramUserId, sourceMessageId,
                    Card.text("Couldn't load this right now. Try again shortly.", Keyboards.backToMenu()));
        }
    }

    private void onLike(long chatId, String telegramUserId, Integer messageId) {
        BrowsingSession session = sessions.getOrCreate(telegramUserId);
        if (isStale(session, messageId)) {
            telegram.removeKeyboard(chatId, messageId);
            return;
        }
        Map<String, Object> candidate = session.current();
        if (candidate != null) {
            try {
                Map<String, Object> ownProfile = backendApiClient.getProfileByTelegramUserId(telegramUserId);
                backendApiClient.recordLike(String.valueOf(ownProfile.get("id")), String.valueOf(candidate.get("id")));
            } catch (Exception e) {
                log.warn("Failed to record like for chat {}: {}", chatId, e.getMessage());
            }
        }
        showNext(chatId, telegramUserId, messageId);
    }

    private void onSkip(long chatId, String telegramUserId, Integer messageId) {
        BrowsingSession session = sessions.getOrCreate(telegramUserId);
        if (isStale(session, messageId)) {
            telegram.removeKeyboard(chatId, messageId);
            return;
        }
        showNext(chatId, telegramUserId, messageId);
    }

    /** A button on an old card that is no longer the tracked one must not act on the current candidate. */
    private boolean isStale(BrowsingSession session, Integer messageId) {
        return messageId != null && session.cardMessageId() != null && !messageId.equals(session.cardMessageId());
    }

    private void showNext(long chatId, String telegramUserId, Integer sourceMessageId) {
        BrowsingSession session = sessions.getOrCreate(telegramUserId);
        Map<String, Object> candidate = session.pollNext();
        if (candidate == null) {
            session.clearBrowsing();
            showCard(chatId, telegramUserId, sourceMessageId,
                    Card.text("That's everyone for now — check back later!", Keyboards.backToMenu()));
            return;
        }
        var keyboard = BrowsingSession.MODE_LIKED_ME.equals(session.mode())
                ? Keyboards.browseLikedMe()
                : Keyboards.browseMatches();
        showCard(chatId, telegramUserId, sourceMessageId, ProfileCardFormatter.toCard(candidate, false, keyboard));
    }

    /**
     * Puts {@code card} on screen in the user's single tracked message. {@code sourceMessageId} is the
     * message a tapped button belonged to (or {@code null} for the persistent menu): if it is not the
     * tracked card it is edited directly and the tracked card is left alone.
     */
    public void showCard(long chatId, String telegramUserId, Integer sourceMessageId, Card card) {
        BrowsingSession session = sessions.getOrCreate(telegramUserId);
        Integer tracked = session.cardMessageId();
        if (sourceMessageId != null && tracked != null && !sourceMessageId.equals(tracked)) {
            cards.replace(chatId, sourceMessageId, false, card);
            return;
        }
        if (tracked == null && sourceMessageId != null) {
            session.trackCard(sourceMessageId, false); // adopt the message the user tapped
        }
        cards.show(chatId, session, card);
    }

    /** Drops queue/candidate state but keeps tracking the card message (it will be reused). */
    public void clearBrowsing(String telegramUserId) {
        sessions.find(telegramUserId).ifPresent(BrowsingSession::clearBrowsing);
    }

    /** Ends the session entirely: the old card's buttons are removed and tracking is forgotten. */
    public void endSession(long chatId, String telegramUserId) {
        sessions.find(telegramUserId).ifPresent(session -> {
            Integer tracked = session.cardMessageId();
            if (tracked != null) {
                telegram.removeKeyboard(chatId, tracked);
            }
        });
        sessions.remove(telegramUserId);
    }
}
