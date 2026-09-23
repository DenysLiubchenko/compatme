package ua.kpi.project.compatme.adapter.out.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for the {@code ground_truth_pairs} collection: the thesis evaluation's
 * "answer key" dataset, imported once from a JSON file and never written to by any other flow.
 * A separate collection from {@code profiles} because it is evaluation-only metadata with a
 * completely different lifecycle (bulk-imported, read-only afterwards) from live profile data.
 */
@Document(collection = "ground_truth_pairs")
public class GroundTruthPairDocument {

    @Id
    private String id;

    private String profileAId;
    private String profileBId;
    private String expectedLabel;
    private Instant importedAt;

    public GroundTruthPairDocument() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProfileAId() {
        return profileAId;
    }

    public void setProfileAId(String profileAId) {
        this.profileAId = profileAId;
    }

    public String getProfileBId() {
        return profileBId;
    }

    public void setProfileBId(String profileBId) {
        this.profileBId = profileBId;
    }

    public String getExpectedLabel() {
        return expectedLabel;
    }

    public void setExpectedLabel(String expectedLabel) {
        this.expectedLabel = expectedLabel;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }
}
