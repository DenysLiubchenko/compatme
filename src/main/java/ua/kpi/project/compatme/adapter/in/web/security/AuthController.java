package ua.kpi.project.compatme.adapter.in.web.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.in.ProfileManagementUseCase;

/** Exchanges verified Telegram Mini App {@code initData} for a short-lived user JWT. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final TelegramInitDataValidator validator;
    private final JwtTokenService tokenService;
    private final ProfileManagementUseCase profiles;

    public AuthController(TelegramInitDataValidator validator, JwtTokenService tokenService,
                          ProfileManagementUseCase profiles) {
        this.validator = validator;
        this.tokenService = tokenService;
        this.profiles = profiles;
    }

    public record TelegramAuthRequest(@NotBlank(message = "initData must not be blank") String initData) {
    }

    /** {@code profileId} is {@code null} until the user has created a profile. */
    public record AuthTokenResponse(String accessToken, String tokenType, long expiresIn, String profileId) {
    }

    @PostMapping("/telegram")
    public ResponseEntity<AuthTokenResponse> authenticate(@Valid @RequestBody TelegramAuthRequest request) {
        String telegramUserId = validator.validateAndExtractUserId(request.initData());
        JwtTokenService.IssuedToken token = tokenService.issue(telegramUserId);
        String profileId;
        try {
            profileId = profiles.getByTelegramUserId(telegramUserId).id().value();
        } catch (ProfileNotFoundException e) {
            profileId = null;
        }
        return ResponseEntity.ok(new AuthTokenResponse(token.value(), "Bearer", token.expiresInSeconds(), profileId));
    }
}
