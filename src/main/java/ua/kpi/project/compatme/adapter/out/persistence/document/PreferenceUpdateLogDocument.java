package ua.kpi.project.compatme.adapter.out.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for the {@code preference_update_log} collection — the audit trail of
 * natural-language preference refinements, required by the thesis spec ("preference update
 * history/log"). Deliberately a separate collection from {@code profiles}: it is
 * append-only/write-heavy and queried independently (e.g. for the thesis evaluation chapter),
 * whereas embedding this history into the profile document would make every profile read/write
 * grow unboundedly over the profile's lifetime.
 */
@Document(collection = "preference_update_log")
public class PreferenceUpdateLogDocument {

    @Id
    private String id;

    private String profileId;
    private String previousPreferenceDescription;
    private String updatedPreferenceDescription;
    private String userMessage;
    private String changeSummary;
    private Instant occurredAt;

    public PreferenceUpdateLogDocument() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public String getPreviousPreferenceDescription() {
        return previousPreferenceDescription;
    }

    public void setPreviousPreferenceDescription(String previousPreferenceDescription) {
        this.previousPreferenceDescription = previousPreferenceDescription;
    }

    public String getUpdatedPreferenceDescription() {
        return updatedPreferenceDescription;
    }

    public void setUpdatedPreferenceDescription(String updatedPreferenceDescription) {
        this.updatedPreferenceDescription = updatedPreferenceDescription;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
