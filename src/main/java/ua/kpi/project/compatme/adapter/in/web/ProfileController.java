package ua.kpi.project.compatme.adapter.in.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    public ProfileController(ProfileManagementUseCase profileManagementUseCase, ProfileWebMapper mapper) {
        this.profileManagementUseCase = profileManagementUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ProfileResponse> createProfile(@Valid @RequestBody ProfileRequest request) {
        Profile created = profileManagementUseCase.createOrUpdateProfile(mapper.toCommand(null, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @PutMapping("/{profileId}")
    public ResponseEntity<ProfileResponse> updateProfile(
            @PathVariable String profileId, @Valid @RequestBody ProfileRequest request) {
        Profile updated = profileManagementUseCase.createOrUpdateProfile(mapper.toCommand(profileId, request));
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @GetMapping("/{profileId}")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable String profileId) {
        Profile profile = profileManagementUseCase.getProfile(ProfileId.of(profileId));
        return ResponseEntity.ok(mapper.toResponse(profile));
    }

    @GetMapping("/by-telegram/{telegramUserId}")
    public ResponseEntity<ProfileResponse> getByTelegramUserId(@PathVariable String telegramUserId) {
        Profile profile = profileManagementUseCase.getByTelegramUserId(telegramUserId);
        return ResponseEntity.ok(mapper.toResponse(profile));
    }

    @GetMapping
    public ResponseEntity<List<ProfileResponse>> listProfiles() {
        List<ProfileResponse> profiles = profileManagementUseCase.listProfiles().stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(profiles);
    }

    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> deleteProfile(@PathVariable String profileId) {
        profileManagementUseCase.deleteProfile(ProfileId.of(profileId));
        return ResponseEntity.noContent().build();
    }
}
