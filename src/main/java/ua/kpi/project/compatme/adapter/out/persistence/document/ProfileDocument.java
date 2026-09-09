package ua.kpi.project.compatme.adapter.out.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Set;

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

    @Field("seekingGenders")
    private Set<String> seekingGenders;

    private String selfDescription;
    private String preferenceDescription;

    private EmbeddingVectorDocument selfEmbedding;
    private EmbeddingVectorDocument preferenceEmbedding;

    private Instant createdAt;
    private Instant updatedAt;

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
}
