package ua.kpi.project.compatme.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kpi.project.compatme.adapter.in.web.dto.EmbeddingGenerationResponse;
import ua.kpi.project.compatme.application.dto.EmbeddingGenerationResult;
import ua.kpi.project.compatme.application.port.in.GenerateEmbeddingsUseCase;
import ua.kpi.project.compatme.domain.model.ProfileId;

/**
 * Inbound REST adapter for triggering (cached) embedding generation for a profile.
 */
@RestController
@RequestMapping("/api/v1/profiles")
public class EmbeddingController {

    private final GenerateEmbeddingsUseCase generateEmbeddingsUseCase;

    public EmbeddingController(GenerateEmbeddingsUseCase generateEmbeddingsUseCase) {
        this.generateEmbeddingsUseCase = generateEmbeddingsUseCase;
    }

    @PostMapping("/{profileId}/embeddings")
    public ResponseEntity<EmbeddingGenerationResponse> generateEmbeddings(@PathVariable String profileId) {
        EmbeddingGenerationResult result = generateEmbeddingsUseCase.generateEmbeddings(ProfileId.of(profileId));
        return ResponseEntity.ok(new EmbeddingGenerationResponse(
                result.selfEmbeddingRecomputed(), result.preferenceEmbeddingRecomputed()));
    }
}
