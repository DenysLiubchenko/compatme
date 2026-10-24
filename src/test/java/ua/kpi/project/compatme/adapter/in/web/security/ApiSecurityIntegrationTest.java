package ua.kpi.project.compatme.adapter.in.web.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ua.kpi.project.compatme.adapter.in.web.EmbeddingController;
import ua.kpi.project.compatme.adapter.in.web.GlobalExceptionHandler;
import ua.kpi.project.compatme.adapter.in.web.LikeController;
import ua.kpi.project.compatme.adapter.in.web.PreferenceController;
import ua.kpi.project.compatme.adapter.in.web.ProfileController;
import ua.kpi.project.compatme.adapter.in.web.ProfilePhotoController;
import ua.kpi.project.compatme.adapter.in.web.ProfileWebMapper;
import ua.kpi.project.compatme.adapter.in.web.RecommendationController;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.port.in.GenerateEmbeddingsUseCase;
import ua.kpi.project.compatme.application.port.in.GetProfilesWhoLikedMeUseCase;
import ua.kpi.project.compatme.application.port.in.PreferenceRefinementUseCase;
import ua.kpi.project.compatme.application.port.in.ProfileManagementUseCase;
import ua.kpi.project.compatme.application.port.in.ProfilePhotoUseCase;
import ua.kpi.project.compatme.application.port.in.RecommendationUseCase;
import ua.kpi.project.compatme.application.port.in.RecordLikeUseCase;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Orientation;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real security filter chain + method security + controllers (use cases mocked):
 * a user must not be able to read-modify, delete or impersonate another user's profile.
 */
@WebMvcTest(controllers = {
        ProfileController.class, LikeController.class, PreferenceController.class, EmbeddingController.class,
        RecommendationController.class, ProfilePhotoController.class, AuthController.class})
@Import({SecurityConfig.class, ProfileAccessPolicy.class, ProfileWebMapper.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "security.jwt-secret=test-secret-test-secret-test-secret-123",
        "security.service-key=test-service-key-1234",
        "telegram.bot-token=123456:TEST-BOT-TOKEN"})
class ApiSecurityIntegrationTest {

    private static final String SERVICE_KEY = "test-service-key-1234";
    private static final String BODY = """
            {"displayName":"Ann","age":25,"gender":"FEMALE","orientation":"STRAIGHT","country":"UA","city":"Kyiv",
             "selfDescription":"I like hiking","preferenceDescription":"Someone kind"}""";

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenService tokens;

    @MockBean private ProfileManagementUseCase profiles;
    @MockBean private RecordLikeUseCase recordLike;
    @MockBean private GetProfilesWhoLikedMeUseCase likedBy;
    @MockBean private PreferenceRefinementUseCase refinement;
    @MockBean private GenerateEmbeddingsUseCase embeddings;
    @MockBean private RecommendationUseCase recommendations;
    @MockBean private ProfilePhotoUseCase photos;

    private Profile aliceProfile;
    private Profile bobProfile;

    @BeforeEach
    void setUp() {
        aliceProfile = profile("alice-profile", "1001");
        bobProfile = profile("bob-profile", "2002");
        when(profiles.getProfile(ProfileId.of("alice-profile"))).thenReturn(aliceProfile);
        when(profiles.getProfile(ProfileId.of("bob-profile"))).thenReturn(bobProfile);
        when(profiles.getProfile(ProfileId.of("missing"))).thenThrow(new ProfileNotFoundException("missing"));
        when(profiles.createOrUpdateProfile(any())).thenReturn(aliceProfile);
    }

    private String bearer(String telegramUserId) {
        return "Bearer " + tokens.issue(telegramUserId).value();
    }

    // ---- authentication -------------------------------------------------------------------

    @Test
    void noCredentials_returns401() throws Exception {
        mvc.perform(get("/api/v1/profiles/alice-profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void garbageBearerToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/profiles/alice-profile").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKey_returns401() throws Exception {
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMDAxIiwiaXNzIjoiY29tcGF0bWUifQ.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        mvc.perform(get("/api/v1/profiles/alice-profile").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongServiceKey_returns401() throws Exception {
        mvc.perform(get("/api/v1/profiles").header("X-Service-Key", "wrong-key-wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointsAndAuthEndpointStayPublic() throws Exception {
        mvc.perform(post("/api/v1/auth/telegram").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initData\":\"garbage\"}"))
                .andExpect(status().isUnauthorized()); // reaches the controller (rejects initData), not the filter
    }

    // ---- authorization: user A vs user B ----------------------------------------------------

    @Test
    void user_cannotUpdateAnotherUsersProfile() throws Exception {
        mvc.perform(put("/api/v1/profiles/bob-profile").header("Authorization", bearer("1001"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(profiles, never()).createOrUpdateProfile(any());
    }

    @Test
    void user_cannotUpdateNonExistentProfileId_toCreateOne() throws Exception {
        mvc.perform(put("/api/v1/profiles/missing").header("Authorization", bearer("1001"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(profiles, never()).createOrUpdateProfile(any());
    }

    @Test
    void user_canUpdateOwnProfile_andOwnerIdIsPinnedToCaller() throws Exception {
        mvc.perform(put("/api/v1/profiles/alice-profile").header("Authorization", bearer("1001"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        verify(profiles).createOrUpdateProfile(argThat(c -> "1001".equals(c.telegramUserId())));
    }

    @Test
    void user_cannotReassignOwnProfileToAnotherTelegramId() throws Exception {
        String body = BODY.replace("{\"displayName\"", "{\"telegramUserId\":\"2002\",\"displayName\"");
        mvc.perform(put("/api/v1/profiles/alice-profile").header("Authorization", bearer("1001"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verify(profiles, never()).createOrUpdateProfile(any());
    }

    @Test
    void user_cannotOverwriteVictimViaPostWithVictimsTelegramId() throws Exception {
        String body = BODY.replace("{\"displayName\"", "{\"telegramUserId\":\"2002\",\"displayName\"");
        mvc.perform(post("/api/v1/profiles").header("Authorization", bearer("1001"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verify(profiles, never()).createOrUpdateProfile(any());
    }

    @Test
    void user_postWithoutTelegramId_usesOwnIdFromToken() throws Exception {
        mvc.perform(post("/api/v1/profiles").header("Authorization", bearer("1001"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        verify(profiles).createOrUpdateProfile(argThat(c -> "1001".equals(c.telegramUserId())));
    }

    @Test
    void user_cannotDeleteAnotherUsersProfile() throws Exception {
        mvc.perform(delete("/api/v1/profiles/bob-profile").header("Authorization", bearer("1001")))
                .andExpect(status().isForbidden());
        verify(profiles, never()).deleteProfile(any());
    }

    @Test
    void user_canDeleteOwnProfile() throws Exception {
        mvc.perform(delete("/api/v1/profiles/alice-profile").header("Authorization", bearer("1001")))
                .andExpect(status().isNoContent());
        verify(profiles).deleteProfile(ProfileId.of("alice-profile"));
    }

    @Test
    void user_cannotLikeAsSomeoneElse() throws Exception {
        mvc.perform(post("/api/v1/profiles/bob-profile/likes").header("Authorization", bearer("1001"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"likedProfileId\":\"alice-profile\"}"))
                .andExpect(status().isForbidden());
        verify(recordLike, never()).recordLike(any(), any());
    }

    @Test
    void user_cannotUploadOrDeletePhotosOfAnotherUser() throws Exception {
        var file = new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[] {1});
        mvc.perform(multipart("/api/v1/profiles/bob-profile/photos").file(file)
                        .header("Authorization", bearer("1001")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/profiles/bob-profile/photos/profile-photos/x.jpg")
                        .header("Authorization", bearer("1001")))
                .andExpect(status().isForbidden());
        verify(photos, never()).addPhoto(any(), any(), any());
        verify(photos, never()).removePhoto(any(), any());
    }

    @Test
    void user_cannotTriggerRefinementEmbeddingsRecommendationsOrLikedByOfAnotherUser() throws Exception {
        String auth = bearer("1001");
        mvc.perform(post("/api/v1/profiles/bob-profile/preference-refinements").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/profiles/bob-profile/embeddings").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/profiles/bob-profile/recommendations").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/profiles/bob-profile/liked-by").header("Authorization", auth))
                .andExpect(status().isForbidden());
    }

    // ---- data exposure -----------------------------------------------------------------------

    @Test
    void user_seesOthersProfileWithoutTelegramId_butOwnWithIt() throws Exception {
        mvc.perform(get("/api/v1/profiles/bob-profile").header("Authorization", bearer("1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("bob-profile"))
                .andExpect(jsonPath("$.telegramUserId").doesNotExist());
        mvc.perform(get("/api/v1/profiles/alice-profile").header("Authorization", bearer("1001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramUserId").value("1001"));
    }

    @Test
    void user_cannotListAllProfiles_orLookUpAnotherByTelegramId() throws Exception {
        mvc.perform(get("/api/v1/profiles").header("Authorization", bearer("1001")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/profiles/by-telegram/2002").header("Authorization", bearer("1001")))
                .andExpect(status().isForbidden());
    }

    // ---- trusted service (bot) ---------------------------------------------------------------

    @Test
    void service_canListAndActOnBehalfOfUsers() throws Exception {
        mvc.perform(get("/api/v1/profiles").header("X-Service-Key", SERVICE_KEY)).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/profiles/bob-profile").header("X-Service-Key", SERVICE_KEY))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/profiles").header("X-Service-Key", SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("{\"displayName\"", "{\"telegramUserId\":\"2002\",\"displayName\"")))
                .andExpect(status().isCreated());
        verify(profiles).createOrUpdateProfile(argThat(c -> "2002".equals(c.telegramUserId())));
    }

    private static Profile profile(String id, String telegramUserId) {
        return Profile.builder()
                .id(ProfileId.of(id)).telegramUserId(telegramUserId).displayName("Name").age(30)
                .gender(Gender.FEMALE).orientation(Orientation.STRAIGHT).seekingGenders(Set.of(Gender.MALE))
                .country("UA").city("Kyiv").selfDescription("self").preferenceDescription("pref")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
}
