package ua.kpi.project.compatme.adapter.in.web;

import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.adapter.in.web.dto.ProfileRequest;
import ua.kpi.project.compatme.adapter.in.web.dto.ProfileResponse;
import ua.kpi.project.compatme.adapter.in.web.dto.RecommendationItem;
import ua.kpi.project.compatme.application.dto.CreateOrUpdateProfileCommand;
import ua.kpi.project.compatme.application.dto.RecommendationResult;
import ua.kpi.project.compatme.domain.model.Profile;

/**
 * Translates between {@code adapter.in.web} request/response DTOs and the framework-agnostic
 * application-layer commands/domain model. This is the only place web-layer types and domain
 * types meet — controllers never construct domain objects directly, and use cases never see web
 * DTOs.
 */
@Component
public class ProfileWebMapper {

    public CreateOrUpdateProfileCommand toCommand(String profileId, ProfileRequest request) {
        return new CreateOrUpdateProfileCommand(
                profileId,
                request.telegramUserId(),
                request.displayName(),
                request.age(),
                request.gender(),
                request.seekingGenders(),
                request.selfDescription(),
                request.preferenceDescription());
    }

    public ProfileResponse toResponse(Profile profile) {
        return new ProfileResponse(
                profile.id().value(),
                profile.telegramUserId(),
                profile.displayName(),
                profile.age(),
                profile.gender(),
                profile.seekingGenders(),
                profile.selfDescription(),
                profile.preferenceDescription(),
                profile.embeddings().hasSelfEmbedding(),
                profile.embeddings().hasPreferenceEmbedding(),
                profile.createdAt(),
                profile.updatedAt());
    }

    public RecommendationItem toRecommendationItem(RecommendationResult result) {
        Profile candidate = result.candidateProfile();
        return new RecommendationItem(
                candidate.id().value(),
                candidate.displayName(),
                candidate.age(),
                result.match().directionalScores().scoreAtoB(),
                result.match().directionalScores().scoreBtoA(),
                result.match().directionalScores().selfSelfSimilarity(),
                result.match().strategy(),
                result.match().aggregatedScore());
    }
}
