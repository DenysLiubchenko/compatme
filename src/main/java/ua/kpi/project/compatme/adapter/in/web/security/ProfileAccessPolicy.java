package ua.kpi.project.compatme.adapter.in.web.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.in.ProfileManagementUseCase;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.util.Objects;

/**
 * Single place that decides whether the caller may act on a profile. A user owns a profile when
 * the profile's {@code telegramUserId} equals the verified subject of their JWT; the trusted
 * service principal (the Telegram bot) may act on any profile. Referenced from
 * {@code @PreAuthorize("@profileAccess...")}. Fails closed: unknown profiles, profiles without a
 * Telegram owner (e.g. seeded sample data) and anonymous callers are all denied, so a probe
 * cannot distinguish "does not exist" from "belongs to someone else".
 */
@Component("profileAccess")
public class ProfileAccessPolicy {

    private final ProfileManagementUseCase profiles;

    public ProfileAccessPolicy(ProfileManagementUseCase profiles) {
        this.profiles = profiles;
    }

    public boolean isService(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(a -> ServiceKeyAuthenticationFilter.ROLE.equals(a.getAuthority()));
    }

    public boolean isOwner(String profileId, Authentication authentication) {
        if (isService(authentication)) {
            return true;
        }
        String caller = callerTelegramUserId(authentication);
        if (caller == null || profileId == null || profileId.isBlank()) {
            return false;
        }
        try {
            return caller.equals(profiles.getProfile(ProfileId.of(profileId)).telegramUserId());
        } catch (ProfileNotFoundException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isSelfOrService(String telegramUserId, Authentication authentication) {
        if (isService(authentication)) {
            return true;
        }
        String caller = callerTelegramUserId(authentication);
        return caller != null && caller.equals(telegramUserId);
    }

    /** Whether private fields (the Telegram id) may be shown to the caller for this profile. */
    public boolean canSeePrivateFields(Profile profile, Authentication authentication) {
        if (isService(authentication)) {
            return true;
        }
        String caller = callerTelegramUserId(authentication);
        return caller != null && caller.equals(profile.telegramUserId());
    }

    /**
     * Determines which Telegram id a create/update may write. Users can only ever write their own
     * (a mismatching value in the body is rejected, never trusted); the service may set it freely.
     */
    public String resolveTelegramUserId(Authentication authentication, String requested) {
        if (isService(authentication)) {
            return requested;
        }
        String caller = callerTelegramUserId(authentication);
        if (caller == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        if (requested != null && !requested.isBlank() && !Objects.equals(requested, caller)) {
            throw new AccessDeniedException("telegramUserId does not match the authenticated user");
        }
        return caller;
    }

    private String callerTelegramUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || isService(authentication)) {
            return null;
        }
        String name = authentication.getName();
        return name == null || name.isBlank() || "anonymousUser".equals(name) ? null : name;
    }
}
