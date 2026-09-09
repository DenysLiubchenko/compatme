package ua.kpi.project.compatme.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ua.kpi.project.compatme.application.dto.EmbeddingGenerationResult;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.in.GenerateEmbeddingsUseCase;
import ua.kpi.project.compatme.application.port.out.EmbeddingProviderPort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.EmbeddingVector;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileEmbeddings;
import ua.kpi.project.compatme.domain.model.ProfileId;
import ua.kpi.project.compatme.domain.service.TextHasher;

import java.time.Instant;

/**
 * Application service implementing embedding generation with caching: an embedding is only
 * recomputed via {@link EmbeddingProviderPort} if the hash of its source text differs from the
 * hash stored alongside the cached {@link EmbeddingVector}, or if the cached vector was produced
 * by a different embedding model. This guarantees embeddings are computed once and cached in
 * MongoDB, never re-computed on every request.
 */
@Service
public class EmbeddingGenerationService implements GenerateEmbeddingsUseCase {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingGenerationService.class);

    private final ProfileRepositoryPort profileRepository;
    private final EmbeddingProviderPort embeddingProvider;

    public EmbeddingGenerationService(ProfileRepositoryPort profileRepository, EmbeddingProviderPort embeddingProvider) {
        this.profileRepository = profileRepository;
        this.embeddingProvider = embeddingProvider;
    }

    @Override
    public EmbeddingGenerationResult generateEmbeddings(ProfileId profileId) {
        Profile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new ProfileNotFoundException(profileId.value()));

        ProfileEmbeddings embeddings = profile.embeddings();
        String modelName = embeddingProvider.modelName();

        boolean selfRecomputed = false;
        EmbeddingVector selfEmbedding = embeddings.selfEmbedding();
        String selfTextHash = TextHasher.sha256Hex(profile.selfDescription());
        if (selfEmbedding == null || !selfEmbedding.isFreshFor(selfTextHash, modelName)) {
            log.info("Recomputing selfDescription embedding for profile {}", profileId);
            selfEmbedding = embeddingProvider.embed(profile.selfDescription());
            selfRecomputed = true;
        } else {
            log.debug("selfDescription embedding for profile {} is up to date, skipping recomputation", profileId);
        }

        boolean preferenceRecomputed = false;
        EmbeddingVector preferenceEmbedding = embeddings.preferenceEmbedding();
        String preferenceTextHash = TextHasher.sha256Hex(profile.preferenceDescription());
        if (preferenceEmbedding == null || !preferenceEmbedding.isFreshFor(preferenceTextHash, modelName)) {
            log.info("Recomputing preferenceDescription embedding for profile {}", profileId);
            preferenceEmbedding = embeddingProvider.embed(profile.preferenceDescription());
            preferenceRecomputed = true;
        } else {
            log.debug("preferenceDescription embedding for profile {} is up to date, skipping recomputation", profileId);
        }

        if (selfRecomputed || preferenceRecomputed) {
            Profile updated = profile.withEmbeddings(
                    new ProfileEmbeddings(selfEmbedding, preferenceEmbedding), Instant.now());
            profileRepository.save(updated);
        }

        return new EmbeddingGenerationResult(selfRecomputed, preferenceRecomputed);
    }
}
