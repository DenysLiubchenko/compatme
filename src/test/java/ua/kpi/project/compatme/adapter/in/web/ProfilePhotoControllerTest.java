package ua.kpi.project.compatme.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ua.kpi.project.compatme.application.exception.PhotoTooLargeException;
import ua.kpi.project.compatme.application.exception.TooManyPhotosException;
import ua.kpi.project.compatme.application.exception.UnsupportedPhotoTypeException;
import ua.kpi.project.compatme.application.port.in.ProfilePhotoUseCase;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the HTTP translation of photo validation errors (no Spring context, no storage). */
@ExtendWith(MockitoExtension.class)
class ProfilePhotoControllerTest {

    @Mock
    private ProfilePhotoUseCase useCase;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProfilePhotoController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "p.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }

    @Test
    void tooLarge_returns413_withClearMessage() throws Exception {
        when(useCase.addPhoto(any(), any(), eq("image/jpeg")))
                .thenThrow(new PhotoTooLargeException(6 * 1024 * 1024, 5 * 1024 * 1024));

        mvc.perform(multipart("/api/v1/profiles/p1/photos").file(file()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("under 5 MB")));
    }

    @Test
    void unsupportedType_returns415() throws Exception {
        when(useCase.addPhoto(any(), any(), any()))
                .thenThrow(new UnsupportedPhotoTypeException("image/gif", List.of("image/jpeg")));

        mvc.perform(multipart("/api/v1/profiles/p1/photos").file(file()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("image/gif")));
    }

    @Test
    void tooManyPhotos_returns409() throws Exception {
        when(useCase.addPhoto(any(), any(), any())).thenThrow(new TooManyPhotosException(6));

        mvc.perform(multipart("/api/v1/profiles/p1/photos").file(file()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("delete one")));
    }

    @Test
    void download_supportsUrnContainingSlash() throws Exception {
        when(useCase.getPhoto(any(), eq("profile-photos/abc.png"))).thenReturn(new byte[] {9});

        mvc.perform(get("/api/v1/profiles/p1/photos/profile-photos/abc.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[] {9}));
    }
}
