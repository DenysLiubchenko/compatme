package ua.kpi.project.compatme.adapter.in.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kpi.project.compatme.adapter.in.web.dto.ProfileRequest;
import ua.kpi.project.compatme.adapter.in.web.dto.ProfileResponse;
import ua.kpi.project.compatme.adapter.in.web.security.ProfileAccessPolicy;
import ua.kpi.project.compatme.application.port.in.ProfileManagementUseCase;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.util.List;

/**
 * Inbound REST adapter for basic profile/user CRUD. Depends only on {@link ProfileManagementUseCase}
 * — never on the persistence adapter or any Spring Data type directly.
 */
@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    private final ProfileManagementUseCase profileManagementUseCase;
    private final ProfileWebMapper mapper;
    private final ProfileAccessPolicy access;

    public ProfileController(
            ProfileManagementUseCase profileManagementUseCase, ProfileWebMapper mapper, ProfileAccessPolicy access) {
        this.profileManagementUseCase = profileManagementUseCase;
        this.mapper = mapper;
        this.access = access;
    }

    /** The owner id comes from the authenticated caller, never from the request body. */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileResponse> createProfile(
            @Valid @RequestBody ProfileRequest request, Authentication authentication) {
        String owner = access.resolveTelegramUserId(authentication, request.telegramUserId());
        Profile created = profileManagementUseCase.createOrUpdateProfile(mapper.toCommand(null, owner, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @PutMapping("/{profileId}")
    @PreAuthorize("@profileAccess.isOwner(#profileId, authentication)")
    public ResponseEntity<ProfileResponse> updateProfile(
            @PathVariable String profileId, @Valid @RequestBody ProfileRequest request,
            Authentication authentication) {
        String owner = access.resolveTelegramUserId(authentication, request.telegramUserId());
        Profile updated = profileManagementUseCase.createOrUpdateProfile(mapper.toCommand(profileId, owner, request));
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @GetMapping("/{profileId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable String profileId, Authentication authentication) {
        Profile profile = profileManagementUseCase.getProfile(ProfileId.of(profileId));
        return ResponseEntity.ok(mapper.toResponse(profile, access.canSeePrivateFields(profile, authentication)));
    }

    @GetMapping("/by-telegram/{telegramUserId}")
    @PreAuthorize("@profileAccess.isSelfOrService(#telegramUserId, authentication)")
    public ResponseEntity<ProfileResponse> getByTelegramUserId(@PathVariable String telegramUserId) {
        Profile profile = profileManagementUseCase.getByTelegramUserId(telegramUserId);
        return ResponseEntity.ok(mapper.toResponse(profile));
    }

    /** Enumerating every profile is an administrative/service capability, not available to end users. */
    @GetMapping
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<List<ProfileResponse>> listProfiles() {
        List<ProfileResponse> profiles = profileManagementUseCase.listProfiles().stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(profiles);
    }

    @DeleteMapping("/{profileId}")
    @PreAuthorize("@profileAccess.isOwner(#profileId, authentication)")
    public ResponseEntity<Void> deleteProfile(@PathVariable String profileId) {
        profileManagementUseCase.deleteProfile(ProfileId.of(profileId));
        return ResponseEntity.noContent().build();
    }
}
