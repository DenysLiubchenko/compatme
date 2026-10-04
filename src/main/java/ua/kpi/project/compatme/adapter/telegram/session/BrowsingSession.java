package ua.kpi.project.compatme.adapter.telegram.session;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/**
 * Adapter-local, in-memory UI session of one user: the candidate queue being browsed plus the
 * Telegram {@code message_id} of the single "current card" message. Not a domain concept.
 */
public class BrowsingSession {

    public static final String MODE_MATCHES = "MATCHES";
    public static final String MODE_LIKED_ME = "LIKED_ME";

    private Deque<Map<String, Object>> queue = new ArrayDeque<>();
    private String mode = MODE_MATCHES;
    private Map<String, Object> current;
    private Integer cardMessageId;
    private boolean cardHasPhoto;
    private Integer profileMessageId;

    public synchronized void startBrowsing(String mode, Deque<Map<String, Object>> queue) {
        this.mode = mode;
        this.queue = queue;
        this.current = null;
    }

    /** Forgets the candidates but keeps tracking the card message so it can be reused. */
    public synchronized void clearBrowsing() {
        this.queue = new ArrayDeque<>();
        this.current = null;
    }

    public synchronized Map<String, Object> pollNext() {
        current = queue.poll();
        return current;
    }

    public synchronized Map<String, Object> current() {
        return current;
    }

    public synchronized String mode() {
        return mode;
    }

    public synchronized Integer cardMessageId() {
        return cardMessageId;
    }

    public synchronized boolean cardHasPhoto() {
        return cardHasPhoto;
    }

    public synchronized void trackCard(Integer messageId, boolean hasPhoto) {
        this.cardMessageId = messageId;
        this.cardHasPhoto = hasPhoto;
    }

    public synchronized void forgetCard() {
        this.cardMessageId = null;
        this.cardHasPhoto = false;
    }

    /** The last "My Profile" message, deleted before a new one is sent so it never stacks. */
    public synchronized Integer profileMessageId() {
        return profileMessageId;
    }

    public synchronized void setProfileMessageId(Integer messageId) {
        this.profileMessageId = messageId;
    }
}
