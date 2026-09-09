package ua.kpi.project.compatme.domain.model;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * A cached embedding for a single piece of text (either {@code selfDescription} or
 * {@code preferenceDescription}). Stores the model name/version and dimensionality alongside the
 * vector so cached vectors can be detected as stale and invalidated if the embedding model
 * changes later, and stores a hash of the source text so re-embedding can be skipped when the
 * underlying text hasn't changed.
 */
public final class EmbeddingVector {

    private final float[] values;
    private final String modelName;
    private final int dimensionality;
    private final String sourceTextHash;
    private final Instant computedAt;

    public EmbeddingVector(float[] values, String modelName, int dimensionality, String sourceTextHash, Instant computedAt) {
        this.values = Objects.requireNonNull(values, "values must not be null").clone();
        this.modelName = Objects.requireNonNull(modelName, "modelName must not be null");
        this.dimensionality = dimensionality;
        this.sourceTextHash = Objects.requireNonNull(sourceTextHash, "sourceTextHash must not be null");
        this.computedAt = Objects.requireNonNull(computedAt, "computedAt must not be null");
        if (values.length != dimensionality) {
            throw new IllegalArgumentException(
                    "values.length (%d) does not match declared dimensionality (%d)"
                            .formatted(values.length, dimensionality));
        }
    }

    /**
     * @return whether this cached embedding is still valid for the given current text and model,
     *     i.e. it does not need to be recomputed.
     */
    public boolean isFreshFor(String currentTextHash, String expectedModelName) {
        return sourceTextHash.equals(currentTextHash) && modelName.equals(expectedModelName);
    }

    public float[] values() {
        return values.clone();
    }

    public String modelName() {
        return modelName;
    }

    public int dimensionality() {
        return dimensionality;
    }

    public String sourceTextHash() {
        return sourceTextHash;
    }

    public Instant computedAt() {
        return computedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmbeddingVector that)) {
            return false;
        }
        return dimensionality == that.dimensionality
                && Arrays.equals(values, that.values)
                && modelName.equals(that.modelName)
                && sourceTextHash.equals(that.sourceTextHash);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(modelName, dimensionality, sourceTextHash);
        result = 31 * result + Arrays.hashCode(values);
        return result;
    }
}
