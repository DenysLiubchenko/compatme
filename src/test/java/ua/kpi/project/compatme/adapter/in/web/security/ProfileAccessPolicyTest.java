package ua.kpi.project.compatme.adapter.in.web.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.in.ProfileManagementUseCase;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Orientation;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileAccessPolicyTest {

    @Mock
    private ProfileManagementUseCase profiles;

    private ProfileAccessPolicy policy;

    private final Authentication alice = user("1001");
    private final Authentication service = UsernamePasswordAuthenticationToken.authenticated(
            "service", null, AuthorityUtils.createAuthorityList(ServiceKeyAuthenticationFilter.ROLE));

    @BeforeEach
    void setUp() {
        policy = new ProfileAccessPolicy(profiles);
        lenient().when(profiles.getProfile(ProfileId.of("alice-profile"))).thenReturn(profileOf("1001"));
        lenient().when(profiles.getProfile(ProfileId.of("bob-profile"))).thenReturn(profileOf("2002"));
        lenient().when(profiles.getProfile(ProfileId.of("seed-profile"))).thenReturn(profileOf(null));
        lenient().when(profiles.getProfile(ProfileId.of("missing"))).thenThrow(new ProfileNotFoundException("missing"));
    }

    @Test
    void owner_isAllowed() {
        assertThat(policy.isOwner("alice-profile", alice)).isTrue();
    }

    @Test
    void anotherUsersProfile_isDenied() {
        assertThat(policy.isOwner("bob-profile", alice)).isFalse();
    }

    @Test
    void missingProfile_isDenied_soExistenceIsNotLeaked() {
        assertThat(policy.isOwner("missing", alice)).isFalse();
    }

    @Test
    void profileWithoutTelegramOwner_isDeniedToUsers() {
        assertThat(policy.isOwner("seed-profile", alice)).isFalse();
    }

    @Test
    void anonymousAndNull_areDenied() {
        Authentication anonymous = new TestingAuthenticationToken("anonymousUser", null);
        anonymous.setAuthenticated(false);
        assertThat(policy.isOwner("alice-profile", anonymous)).isFalse();
        assertThat(policy.isOwner("alice-profile", null)).isFalse();
    }

    @Test
    void service_mayActOnAnyProfile() {
        assertThat(policy.isOwner("bob-profile", service)).isTrue();
        assertThat(policy.isSelfOrService("anything", service)).isTrue();
    }

    @Test
    void selfLookupByTelegramId_onlyForSameUser() {
        assertThat(policy.isSelfOrService("1001", alice)).isTrue();
        assertThat(policy.isSelfOrService("2002", alice)).isFalse();
    }

    @Test
    void resolveTelegramUserId_forcesCallersOwnId() {
        assertThat(policy.resolveTelegramUserId(alice, null)).isEqualTo("1001");
        assertThat(policy.resolveTelegramUserId(alice, "1001")).isEqualTo("1001");
    }

    @Test
    void resolveTelegramUserId_rejectsImpersonationViaBody() {
        assertThatThrownBy(() -> policy.resolveTelegramUserId(alice, "2002"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void resolveTelegramUserId_serviceMaySetAnyId() {
        assertThat(policy.resolveTelegramUserId(service, "2002")).isEqualTo("2002");
    }

    @Test
    void privateFields_visibleOnlyToOwnerAndService() {
        Profile bob = profileOf("2002");
        assertThat(policy.canSeePrivateFields(bob, alice)).isFalse();
        assertThat(policy.canSeePrivateFields(bob, user("2002"))).isTrue();
        assertThat(policy.canSeePrivateFields(bob, service)).isTrue();
    }

    private static Authentication user(String telegramUserId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                telegramUserId, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    private static Profile profileOf(String telegramUserId) {
        return Profile.builder()
                .id(ProfileId.generate()).telegramUserId(telegramUserId).displayName("Name").age(30)
                .gender(Gender.FEMALE).orientation(Orientation.STRAIGHT).seekingGenders(Set.of(Gender.MALE))
                .country("UA").city("Kyiv").selfDescription("self").preferenceDescription("pref")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
}
