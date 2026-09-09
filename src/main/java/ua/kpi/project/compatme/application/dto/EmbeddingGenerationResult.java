package ua.kpi.project.compatme.application.dto;

/**
 * Result of {@code GenerateEmbeddingsUseCase}, reporting whether each embedding was actually
 * recomputed or served from cache (skipped because the underlying text was unchanged).
 */
public record EmbeddingGenerationResult(boolean selfEmbeddingRecomputed, boolean preferenceEmbeddingRecomputed) {
}
