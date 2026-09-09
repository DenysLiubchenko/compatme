package ua.kpi.project.compatme.adapter.out.persistence.document;

import java.time.Instant;

/**
 * Embedded MongoDB representation of a cached embedding vector. Stored inline within
 * {@link ProfileDocument} rather than a separate collection: embeddings are always read/written
 * together with their owning profile (never queried independently), so embedding them avoids an
 * extra round-trip and keeps the single-profile read atomic. The tradeoff is a larger profile
 * document (roughly 768 floats x 2 fields); acceptable at the hundreds-to-thousands-of-profiles
 * scale this thesis prototype targets.
 */
public class EmbeddingVectorDocument {

    private float[] values;
    private String modelName;
    private int dimensionality;
    private String sourceTextHash;
    private Instant computedAt;

    public EmbeddingVectorDocument() {
    }

    public EmbeddingVectorDocument(float[] values, String modelName, int dimensionality, String sourceTextHash, Instant computedAt) {
        this.values = values;
        this.modelName = modelName;
        this.dimensionality = dimensionality;
        this.sourceTextHash = sourceTextHash;
        this.computedAt = computedAt;
    }

    public float[] getValues() {
        return values;
    }

    public void setValues(float[] values) {
        this.values = values;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getDimensionality() {
        return dimensionality;
    }

    public void setDimensionality(int dimensionality) {
        this.dimensionality = dimensionality;
    }

    public String getSourceTextHash() {
        return sourceTextHash;
    }

    public void setSourceTextHash(String sourceTextHash) {
        this.sourceTextHash = sourceTextHash;
    }

    public Instant getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
