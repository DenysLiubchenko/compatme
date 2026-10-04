package ua.kpi.project.compatme.adapter.in.web;

import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.adapter.in.web.dto.ProfileRequest;
import ua.kpi.project.compatme.adapter.in.web.dto.ProfileResponse;
import ua.kpi.project.compatme.adapter.in.web.dto.RecommendationItem;
import ua.kpi.project.compatme.application.dto.CreateOrUpdateProfileCommand;
import ua.kpi.project.compatme.application.dto.RecommendationResult;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.OptionalProfileFields;

import java.util.List;

/**
 * Translates between {@code adapter.in.web} request/response DTOs and the framework-agnostic
 * application-layer commands/domain model. This is the only place web-layer types and domain
 * types meet — controllers never construct domain objects directly, and use cases never see web
 * DTOs.
 */
@Component
public class ProfileWebMapper {

    /**
     * Shown instead of {@code null} in {@link ProfileResponse#telegramUserId()} for profiles that
     * have no real Telegram account (e.g. imported from sample/evaluation JSON datasets), so API
     * consumers always see an explicit, readable value rather than an absent/null field.
     */
    static final String NO_TELEGRAM_ID_PLACEHOLDER = "N/A (no Telegram account, sample/evaluation profile)";

    public CreateOrUpdateProfileCommand toCommand(String profileId, ProfileRequest request) {
        return new CreateOrUpdateProfileCommand(
                profileId,
                request.telegramUserId(),
                request.displayName(),
                request.age(),
                request.gender(),
                request.orientation(),
                request.seekingGenders(),
                request.selfDescription(),
                request.preferenceDescription(),
                request.archetypeIds(),
                request.country(),
                request.city(),
                // Legacy clients used photoUrns for external URLs. Opaque storage URNs are owned
                // exclusively by ProfilePhotoUseCase and must not be writable through profile PUT.
                externalPhotoUrls(request.photoUrns()),
                request.optionalFields(),
                request.searchScope(),
                request.minPreferredAge(),
                request.maxPreferredAge());
    }

    private static List<String> externalPhotoUrls(List<String> references) {
        if (references == null) {
            return List.of();
        }
        return references.stream()
                .filter(value -> value != null
                        && (value.startsWith("http://") || value.startsWith("https://")))
                .toList();
    }

    public ProfileResponse toResponse(Profile profile) {
        return new ProfileResponse(
                profile.id().value(),
                profile.telegramUserId() == null ? NO_TELEGRAM_ID_PLACEHOLDER : profile.telegramUserId(),
                profile.displayName(),
                profile.age(),
                profile.gender(),
                profile.orientation(),
                profile.country(),
                profile.city(),
                profile.searchScope(),
                profile.minPreferredAge(),
                profile.maxPreferredAge(),
                profile.seekingGenders(),
                profile.selfDescription(),
                profile.preferenceDescription(),
                profile.embeddings().hasSelfEmbedding(),
                profile.embeddings().hasPreferenceEmbedding(),
                profile.createdAt(),
                profile.updatedAt(),
                profile.archetypeIds(),
                profile.photoUrls(),
                new OptionalProfileFields(profile.status(), profile.bodyType(), profile.diet(), profile.drinks(),
                        profile.drugs(), profile.education(), profile.ethnicity(), profile.height(), profile.income(),
                        profile.job(), profile.lastOnline(), profile.offspring(), profile.pets(), profile.religion(),
                        profile.sign(), profile.smokes(), profile.speaks()),
                profile.photoUrns());
    }

    public RecommendationItem toRecommendationItem(RecommendationResult result) {
        Profile candidate = result.candidateProfile();
        return new RecommendationItem(
                candidate.id().value(),
                candidate.displayName(),
                candidate.age(),
                candidate.photoUrls().isEmpty() ? null : candidate.photoUrls().get(0),
                candidate.photoUrns(),
                candidate.country(),
                candidate.city(),
                candidate.selfDescription(),
                candidate.preferenceDescription(),
                result.match().directionalScores().scoreAtoB(),
                result.match().directionalScores().scoreBtoA(),
                result.match().directionalScores().selfSelfSimilarity(),
                result.match().aggregatedScore());
    }
}
