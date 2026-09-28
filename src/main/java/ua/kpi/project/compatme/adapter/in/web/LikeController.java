package ua.kpi.project.compatme.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kpi.project.compatme.adapter.in.web.dto.ProfileResponse;
import ua.kpi.project.compatme.adapter.in.web.dto.RecordLikeRequest;
import ua.kpi.project.compatme.adapter.in.web.dto.RecordLikeResponse;
import ua.kpi.project.compatme.application.dto.RecordLikeResult;
import ua.kpi.project.compatme.application.port.in.GetProfilesWhoLikedMeUseCase;
import ua.kpi.project.compatme.application.port.in.RecordLikeUseCase;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.util.List;

/**
 * Inbound REST adapter for the "like"/"who liked me" concept — an explicit user action, entirely
 * separate from compatibility scoring/recommendations (see {@code domain.model.Like}'s Javadoc).
 * Depends only on {@link RecordLikeUseCase}/{@link GetProfilesWhoLikedMeUseCase}, never on the
 * persistence adapter directly.
 */
@RestController
@RequestMapping("/api/v1/profiles")
public class LikeController {

    private final RecordLikeUseCase recordLikeUseCase;
    private final GetProfilesWhoLikedMeUseCase getProfilesWhoLikedMeUseCase;
    private final ProfileWebMapper mapper;

    public LikeController(
            RecordLikeUseCase recordLikeUseCase,
            GetProfilesWhoLikedMeUseCase getProfilesWhoLikedMeUseCase,
            ProfileWebMapper mapper) {
        this.recordLikeUseCase = recordLikeUseCase;
        this.getProfilesWhoLikedMeUseCase = getProfilesWhoLikedMeUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/{likerId}/likes")
    public ResponseEntity<RecordLikeResponse> recordLike(
            @PathVariable String likerId, @RequestBody RecordLikeRequest request) {
        RecordLikeResult result = recordLikeUseCase.recordLike(ProfileId.of(likerId), ProfileId.of(request.likedProfileId()));
        return ResponseEntity.ok(new RecordLikeResponse(result.mutualMatch()));
    }

    @GetMapping("/{profileId}/liked-by")
    public ResponseEntity<List<ProfileResponse>> getProfilesWhoLikedMe(@PathVariable String profileId) {
        List<ProfileResponse> profiles = getProfilesWhoLikedMeUseCase.getProfilesWhoLikedMe(ProfileId.of(profileId))
                .stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(profiles);
    }
}
