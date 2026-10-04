package ua.kpi.project.compatme.adapter.telegram.state;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * MongoDB document for the {@code telegram_conversation_states} collection: one document per
 * Telegram user, holding their in-progress onboarding state so it survives a bot restart during
 * development. Fields are flattened (no nested "draft" object) to keep Spring Data mapping
 * trivial, matching the plain-mutable-class style already used by {@code ProfileDocument}.
 *
 * <p>This is deliberately a self-contained {@code adapter.telegram} concern — a separate
 * collection from {@code profiles}, with no port/adapter indirection, since the
 * application/domain layers have no reason to know a "conversation" exists.
 */
@Document(collection = "telegram_conversation_states")
public class ConversationStateDocument {

    @Id
    private String telegramUserId;

    private String step;

    private String name;
    private Integer age;
    private String gender;
    private String orientation;
    private List<String> seekingGenders;
    private String country;
    private String city;
    private String searchScope;
    private Integer minPreferredAge;
    private Integer maxPreferredAge;
    private String pendingCountry;
    private String pendingCity;
    private String selfDescription;
    private String preferenceDescription;
    private ua.kpi.project.compatme.domain.model.OptionalProfileFields optionalFields;
    private boolean returnToReview;
    private List<String> photoUrns;

    public ConversationStateDocument() {
    }

    public Integer getMinPreferredAge() { return minPreferredAge; }
    public void setMinPreferredAge(Integer v) { this.minPreferredAge = v; }
    public Integer getMaxPreferredAge() { return maxPreferredAge; }
    public void setMaxPreferredAge(Integer v) { this.maxPreferredAge = v; }
    public String getSearchScope() { return searchScope; }
    public void setSearchScope(String searchScope) { this.searchScope = searchScope; }

    public String getTelegramUserId() {
        return telegramUserId;
    }

    public void setTelegramUserId(String telegramUserId) {
        this.telegramUserId = telegramUserId;
    }

    public String getStep() {
        return step;
    }

    public void setStep(String step) {
        this.step = step;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getOrientation() { return orientation; }
    public void setOrientation(String orientation) { this.orientation = orientation; }

    public List<String> getSeekingGenders() {
        return seekingGenders;
    }

    public void setSeekingGenders(List<String> seekingGenders) {
        this.seekingGenders = seekingGenders;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getPendingCountry() {
        return pendingCountry;
    }

    public void setPendingCountry(String pendingCountry) {
        this.pendingCountry = pendingCountry;
    }

    public String getPendingCity() {
        return pendingCity;
    }

    public void setPendingCity(String pendingCity) {
        this.pendingCity = pendingCity;
    }

    public String getSelfDescription() {
        return selfDescription;
    }

    public void setSelfDescription(String selfDescription) {
        this.selfDescription = selfDescription;
    }

    public String getPreferenceDescription() {
        return preferenceDescription;
    }

    public void setPreferenceDescription(String preferenceDescription) {
        this.preferenceDescription = preferenceDescription;
    }

    public ua.kpi.project.compatme.domain.model.OptionalProfileFields getOptionalFields() { return optionalFields; }
    public void setOptionalFields(ua.kpi.project.compatme.domain.model.OptionalProfileFields optionalFields) { this.optionalFields = optionalFields; }

    public boolean isReturnToReview() {
        return returnToReview;
    }

    public void setReturnToReview(boolean returnToReview) {
        this.returnToReview = returnToReview;
    }

    public List<String> getPhotoUrns() {
        return photoUrns;
    }

    public void setPhotoUrns(List<String> photoUrns) {
        this.photoUrns = photoUrns;
    }
}
