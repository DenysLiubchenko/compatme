package ua.kpi.project.compatme.adapter.telegram.session;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link BrowsingSession}s keyed by Telegram user id (lost on restart by design). */
public class BrowsingSessionStore {

    private final Map<String, BrowsingSession> sessions = new ConcurrentHashMap<>();

    public BrowsingSession getOrCreate(String telegramUserId) {
        return sessions.computeIfAbsent(telegramUserId, id -> new BrowsingSession());
    }

    public Optional<BrowsingSession> find(String telegramUserId) {
        return Optional.ofNullable(sessions.get(telegramUserId));
    }

    public void remove(String telegramUserId) {
        sessions.remove(telegramUserId);
    }
}
