package ua.kpi.project.compatme.domain.model;

/**
 * Bundles the two separately-computed embeddings for a profile. The split is required for
 * reciprocal scoring: {@code selfDescription} and {@code preferenceDescription} must never be
 * merged into a single embedding.
 *
 * <p>Either field may be {@code null} if that embedding has not been computed yet (e.g.
 * immediately after profile creation, before {@code GenerateEmbeddingsUseCase} runs).
 */
public record ProfileEmbeddings(EmbeddingVector selfEmbedding, EmbeddingVector preferenceEmbedding) {

    public static ProfileEmbeddings empty() {
        return new ProfileEmbeddings(null, null);
    }

    public boolean hasSelfEmbedding() {
        return selfEmbedding != null;
    }

    public boolean hasPreferenceEmbedding() {
        return preferenceEmbedding != null;
    }

    public boolean isComplete() {
        return hasSelfEmbedding() && hasPreferenceEmbedding();
    }

    public ProfileEmbeddings withSelfEmbedding(EmbeddingVector newSelfEmbedding) {
        return new ProfileEmbeddings(newSelfEmbedding, preferenceEmbedding);
    }

    public ProfileEmbeddings withPreferenceEmbedding(EmbeddingVector newPreferenceEmbedding) {
        return new ProfileEmbeddings(selfEmbedding, newPreferenceEmbedding);
    }
}
