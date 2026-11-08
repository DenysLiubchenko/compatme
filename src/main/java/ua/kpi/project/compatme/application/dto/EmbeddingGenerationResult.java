package ua.kpi.project.compatme.application.dto;

/**
 * Result of {@code GenerateEmbeddingsUseCase}, reporting whether each embedding was actually
 * recomputed or served from cache (skipped because the underlying text was unchanged).
 *
 * @param pending {@code true} when the embedding provider was unavailable, so at least one
 *                embedding is still missing or stale; it is completed automatically later
 */
public record EmbeddingGenerationResult(
        boolean selfEmbeddingRecomputed, boolean preferenceEmbeddingRecomputed, boolean pending) {
}
