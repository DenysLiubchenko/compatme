package ua.kpi.project.compatme.adapter.telegram.state;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import ua.kpi.project.compatme.domain.model.OptionalProfileFields;

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
    private String orientation;
    private final Set<String> seekingGenders = new LinkedHashSet<>();
    private String country;
    private String city;

    private Integer minPreferredAge;
    private Integer maxPreferredAge;

    /** Default location search scope name (CITY, COUNTRY or WORLDWIDE). */
    private String searchScope = "WORLDWIDE";

    /** Reverse-geocode (or manual-entry) candidate awaiting the "is this correct?" confirmation. */
    private String pendingCountry;
    private String pendingCity;

    private String selfDescription;
    private String preferenceDescription;
    private OptionalProfileFields optionalFields = OptionalProfileFields.empty();

    /** User-provided photo URL/URN references (max five); never fetched or analyzed. */
    private final List<String> photoUrns = new ArrayList<>();

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

    public String orientation() { return orientation; }
    public void setOrientation(String orientation) { this.orientation = orientation; }

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

    public Integer minPreferredAge() { return minPreferredAge; }
    public Integer maxPreferredAge() { return maxPreferredAge; }

    public void setPreferredAgeRange(Integer min, Integer max) {
        this.minPreferredAge = min;
        this.maxPreferredAge = max;
    }

    public String searchScope() {
        return searchScope;
    }

    public void setSearchScope(String searchScope) {
        this.searchScope = searchScope == null ? "WORLDWIDE" : searchScope;
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

    public OptionalProfileFields optionalFields() { return optionalFields; }
    public void setOptionalFields(OptionalProfileFields optionalFields) {
        this.optionalFields = optionalFields == null ? OptionalProfileFields.empty() : optionalFields;
    }

    public boolean isReturnToReview() {
        return returnToReview;
    }

    public void setReturnToReview(boolean returnToReview) {
        this.returnToReview = returnToReview;
    }

    public List<String> photoUrns() {
        return photoUrns;
    }

    public boolean addPhotoUrn(String photoUrn) {
        if (photoUrns.size() >= 6) {
            return false;
        }
        photoUrns.add(photoUrn);
        return true;
    }

    public boolean removePhotoUrnAt(int index) {
        if (index < 0 || index >= photoUrns.size()) {
            return false;
        }
        photoUrns.remove(index);
        return true;
    }
}
