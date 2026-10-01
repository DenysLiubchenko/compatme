package ua.kpi.project.compatme.adapter.out.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import ua.kpi.project.compatme.domain.model.DrinkingFrequency;
import ua.kpi.project.compatme.domain.model.DrugUseFrequency;
import ua.kpi.project.compatme.domain.model.Orientation;
import ua.kpi.project.compatme.domain.model.RelationshipStatus;
import ua.kpi.project.compatme.domain.model.SmokingStatus;

/**
 * MongoDB document for the {@code profiles} collection. This class — and the rest of
 * {@code adapter.out.persistence} — is the ONLY place Spring Data / MongoDB annotations appear
 * for profile data; the domain {@code Profile} aggregate is deliberately kept free of them.
 * {@link ua.kpi.project.compatme.adapter.out.persistence.ProfilePersistenceMapper} converts
 * between the two representations at the adapter boundary.
 */
@Document(collection = "profiles")
public class ProfileDocument {

    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String telegramUserId;

    private String displayName;
    private Integer age;
    private String gender;
    private Orientation orientation;
    private String country;
    private String city;

    @Field("seekingGenders")
    private Set<String> seekingGenders;

    private String selfDescription;
    private String preferenceDescription;

    private EmbeddingVectorDocument selfEmbedding;
    private EmbeddingVectorDocument preferenceEmbedding;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Thesis-evaluation-only metadata (synthetic archetype tags carried over from the seed
     * dataset). Purely descriptive — never read by scoring logic. See
     * {@link ua.kpi.project.compatme.domain.model.Profile#archetypeIds()}.
     */
    private List<Integer> archetypeIds;

    /** Photo URLs. Only URLs are stored — never image bytes. */
    private List<String> photoUrls;

    private RelationshipStatus status;
    private String bodyType;
    private String diet;
    private DrinkingFrequency drinks;
    private DrugUseFrequency drugs;
    private String education;
    private List<String> ethnicity;
    private Double height;
    private Integer income;
    private String job;
    private Instant lastOnline;
    private String offspring;
    private String pets;
    private String religion;
    private String sign;
    private SmokingStatus smokes;
    private List<String> speaks;
    private List<String> photoUrns;

    public ProfileDocument() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTelegramUserId() {
        return telegramUserId;
    }

    public void setTelegramUserId(String telegramUserId) {
        this.telegramUserId = telegramUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
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

    public Orientation getOrientation() { return orientation; }
    public void setOrientation(Orientation orientation) { this.orientation = orientation; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public Set<String> getSeekingGenders() {
        return seekingGenders;
    }

    public void setSeekingGenders(Set<String> seekingGenders) {
        this.seekingGenders = seekingGenders;
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

    public EmbeddingVectorDocument getSelfEmbedding() {
        return selfEmbedding;
    }

    public void setSelfEmbedding(EmbeddingVectorDocument selfEmbedding) {
        this.selfEmbedding = selfEmbedding;
    }

    public EmbeddingVectorDocument getPreferenceEmbedding() {
        return preferenceEmbedding;
    }

    public void setPreferenceEmbedding(EmbeddingVectorDocument preferenceEmbedding) {
        this.preferenceEmbedding = preferenceEmbedding;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Integer> getArchetypeIds() {
        return archetypeIds;
    }

    public void setArchetypeIds(List<Integer> archetypeIds) {
        this.archetypeIds = archetypeIds;
    }

    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(List<String> photoUrls) {
        this.photoUrls = photoUrls;
    }

    public RelationshipStatus getStatus() { return status; }
    public void setStatus(RelationshipStatus status) { this.status = status; }
    public String getBodyType() { return bodyType; }
    public void setBodyType(String bodyType) { this.bodyType = bodyType; }
    public String getDiet() { return diet; }
    public void setDiet(String diet) { this.diet = diet; }
    public DrinkingFrequency getDrinks() { return drinks; }
    public void setDrinks(DrinkingFrequency drinks) { this.drinks = drinks; }
    public DrugUseFrequency getDrugs() { return drugs; }
    public void setDrugs(DrugUseFrequency drugs) { this.drugs = drugs; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public List<String> getEthnicity() { return ethnicity; }
    public void setEthnicity(List<String> ethnicity) { this.ethnicity = ethnicity; }
    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }
    public Integer getIncome() { return income; }
    public void setIncome(Integer income) { this.income = income; }
    public String getJob() { return job; }
    public void setJob(String job) { this.job = job; }
    public Instant getLastOnline() { return lastOnline; }
    public void setLastOnline(Instant lastOnline) { this.lastOnline = lastOnline; }
    public String getOffspring() { return offspring; }
    public void setOffspring(String offspring) { this.offspring = offspring; }
    public String getPets() { return pets; }
    public void setPets(String pets) { this.pets = pets; }
    public String getReligion() { return religion; }
    public void setReligion(String religion) { this.religion = religion; }
    public String getSign() { return sign; }
    public void setSign(String sign) { this.sign = sign; }
    public SmokingStatus getSmokes() { return smokes; }
    public void setSmokes(SmokingStatus smokes) { this.smokes = smokes; }
    public List<String> getSpeaks() { return speaks; }
    public void setSpeaks(List<String> speaks) { this.speaks = speaks; }
    public List<String> getPhotoUrns() { return photoUrns; }
    public void setPhotoUrns(List<String> photoUrns) { this.photoUrns = photoUrns; }
}
