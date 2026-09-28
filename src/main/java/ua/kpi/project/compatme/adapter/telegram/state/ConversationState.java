package ua.kpi.project.compatme.adapter.telegram.state;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * In-progress conversation state for one Telegram user: the current onboarding step plus every
 * profile field collected so far in this session. NOT a domain object — this is purely an
 * {@code adapter.telegram} concern; the application/domain layers only ever see the final,
 * complete profile once {@link ConversationStep#REVIEW} is confirmed and saved via the existing
 * {@code ProfileManagementUseCase}.
 *
 * <p>Mutable by design: {@code ConversationFlowHandler} updates one field per step and persists
 * the whole state back via {@link ConversationStateStore} after every incoming update.
 */
public class ConversationState {

    private final String telegramUserId;
    private ConversationStep step;

    private String name;
    private Integer age;
    private String gender;
    private final Set<String> seekingGenders = new LinkedHashSet<>();
    private String country;
    private String city;

    /** Reverse-geocode (or manual-entry) candidate awaiting the "is this correct?" confirmation. */
    private String pendingCountry;
    private String pendingCity;

    private String selfDescription;
    private String preferenceDescription;

    /** Telegram {@code file_id}s collected so far during onboarding/editing (max 5, enforced by {@code ConversationFlowHandler}). */
    private final List<String> photoFileIds = new ArrayList<>();

    /**
     * When {@code true}, the current sub-flow was entered via a Review "Edit ..." button: on
     * completing just that field group, the flow jumps straight back to {@link
     * ConversationStep#REVIEW} instead of continuing linearly, and every other already-collected
     * field is left untouched.
     */
    private boolean returnToReview;

    public ConversationState(String telegramUserId) {
        this.telegramUserId = telegramUserId;
        this.step = ConversationStep.WELCOME;
    }

    public String telegramUserId() {
        return telegramUserId;
    }

    public ConversationStep step() {
        return step;
    }

    public void setStep(ConversationStep step) {
        this.step = step;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer age() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String gender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Set<String> seekingGenders() {
        return seekingGenders;
    }

    public void toggleSeekingGender(String gender) {
        if (!seekingGenders.remove(gender)) {
            seekingGenders.add(gender);
        }
    }

    public String country() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String city() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String pendingCountry() {
        return pendingCountry;
    }

    public void setPendingCountry(String pendingCountry) {
        this.pendingCountry = pendingCountry;
    }

    public String pendingCity() {
        return pendingCity;
    }

    public void setPendingCity(String pendingCity) {
        this.pendingCity = pendingCity;
    }

    /** Commits {@link #pendingCountry}/{@link #pendingCity} as the final, confirmed location. */
    public void confirmPendingLocation() {
        this.country = pendingCountry;
        this.city = pendingCity;
        this.pendingCountry = null;
        this.pendingCity = null;
    }

    public String selfDescription() {
        return selfDescription;
    }

    public void setSelfDescription(String selfDescription) {
        this.selfDescription = selfDescription;
    }

    public String preferenceDescription() {
        return preferenceDescription;
    }

    public void setPreferenceDescription(String preferenceDescription) {
        this.preferenceDescription = preferenceDescription;
    }

    public boolean isReturnToReview() {
        return returnToReview;
    }

    public void setReturnToReview(boolean returnToReview) {
        this.returnToReview = returnToReview;
    }

    public List<String> photoFileIds() {
        return photoFileIds;
    }

    public boolean addPhotoFileId(String fileId) {
        if (photoFileIds.size() >= 5) {
            return false;
        }
        photoFileIds.add(fileId);
        return true;
    }

    public boolean removePhotoFileIdAt(int index) {
        if (index < 0 || index >= photoFileIds.size()) {
            return false;
        }
        photoFileIds.remove(index);
        return true;
    }
}
