package ua.kpi.project.compatme.application.port.in;

import ua.kpi.project.compatme.application.dto.EmbeddingGenerationResult;
import ua.kpi.project.compatme.domain.model.ProfileId;

/**
 * Inbound use case for triggering embedding generation for a profile, with caching: an embedding
 * is only recomputed if the underlying description text has changed since it was last embedded.
 */
public interface GenerateEmbeddingsUseCase {

    EmbeddingGenerationResult generateEmbeddings(ProfileId profileId);
}
