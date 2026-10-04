package ua.kpi.project.compatme.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.kpi.project.compatme.application.config.PhotoPolicyProperties;
import ua.kpi.project.compatme.application.exception.PhotoNotFoundException;
import ua.kpi.project.compatme.application.exception.PhotoTooLargeException;
import ua.kpi.project.compatme.application.exception.ProfileNotFoundException;
import ua.kpi.project.compatme.application.exception.TooManyPhotosException;
import ua.kpi.project.compatme.application.exception.UnsupportedPhotoTypeException;
import ua.kpi.project.compatme.application.port.out.PhotoStoragePort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Orientation;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileEmbeddings;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfilePhotoServiceTest {

    private static final long MAX_SIZE = 5 * 1024 * 1024;
    private static final int MAX_PHOTOS = 6;

    @Mock
    private ProfileRepositoryPort profileRepository;

    @Mock
    private PhotoStoragePort photoStorage;

    private ProfilePhotoService service;

    @BeforeEach
    void setUp() {
        service = new ProfilePhotoService(profileRepository, photoStorage,
                new PhotoPolicyProperties(MAX_SIZE, List.of("image/jpeg", "image/png", "image/webp"), MAX_PHOTOS));
    }

    @Test
    void addPhoto_storesAndAttachesUrn_whenValid() {
        Profile profile = profileWithPhotos(1);
        when(profileRepository.findById(profile.id())).thenReturn(Optional.of(profile));
        when(photoStorage.store(any(), anyString())).thenReturn("profile-photos/new.jpg");
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Profile result = service.addPhoto(profile.id(), new byte[100], "image/jpeg");

        assertThat(result.photoUrns()).hasSize(2).endsWith("profile-photos/new.jpg");
    }

    @Test
    void addPhoto_acceptsExactlyMaxSize() {
        Profile profile = profileWithPhotos(0);
        when(profileRepository.findById(profile.id())).thenReturn(Optional.of(profile));
        when(photoStorage.store(any(), anyString())).thenReturn("profile-photos/x.png");
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addPhoto(profile.id(), new byte[(int) MAX_SIZE], "image/png");

        verify(photoStorage).store(any(), anyString());
    }

    @Test
    void addPhoto_rejectsOneByteOverMaxSize_withoutTouchingStorage() {
        Profile profile = profileWithPhotos(0);
        when(profileRepository.findById(profile.id())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.addPhoto(profile.id(), new byte[(int) MAX_SIZE + 1], "image/jpeg"))
                .isInstanceOf(PhotoTooLargeException.class)
                .hasMessageContaining("5 MB");
        verifyNoInteractions(photoStorage);
        verify(profileRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp", "IMAGE/JPEG", "image/png; charset=binary"})
    void addPhoto_acceptsAllowedTypes_caseInsensitiveAndIgnoringParameters(String type) {
        Profile profile = profileWithPhotos(0);
        when(profileRepository.findById(profile.id())).thenReturn(Optional.of(profile));
        when(photoStorage.store(any(), anyString())).thenReturn("profile-photos/x");
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.addPhoto(profile.id(), new byte[10], type);

        verify(photoStorage).store(any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/gif", "application/pdf", "text/plain", ""})
    void addPhoto_rejectsUnsupportedTypes_withoutTouchingStorage(String type) {
        Profile profile = profileWithPhotos(0);
        when(profileRepository.findById(profile.id())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.addPhoto(profile.id(), new byte[10], type))
                .isInstanceOf(UnsupportedPhotoTypeException.class)
                .hasMessageContaining("image/jpeg");
        verifyNoInteractions(photoStorage);
    }

    @Test
    void addPhoto_rejectsNullContentType() {
        Profile profile = profileWithPhotos(0);
        when(profileRepository.findById(profile.id())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.addPhoto(profile.id(), new byte[10], null))
                .isInstanceOf(UnsupportedPhotoTypeException.class);
    }

    @Test
    void addPhoto_acceptsWhenOneBelowMaxCount() {
        Profile profile = profileWithPhotos(MAX_PHOTOS - 1);
        when(profileRepository.findById(profile.id())).thenReturn(Optional.of(profile));
        when(photoStorage.store(any(), anyString())).thenReturn("profile-photos/last.jpg");
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Profile result = service.addPhoto(profile.id(), new byte[10], "image/jpeg");

        assertThat(result.photoUrns()).hasSize(MAX_PHOTOS);
    }

    @Test
    void addPhoto_rejectsWhenAtMaxCount_withoutTouchingStorage() {
        Profile profile = profileWithPhotos(MAX_PHOTOS);
        when(profileRepository.findById(profile.id())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.addPhoto(profile.id(), new byte[10], "image/jpeg"))
                .isInstanceOf(TooManyPhotosException.class)
                .hasMessageContaining("delete one");
        verifyNoInteractions(photoStorage);
    }

    @Test
    void addPhoto_rejectsEmptyBytes() {
        Profile profile = profileWithPhotos(0);
        when(profileRepository.findById(profile.id())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.addPhoto(profile.id(), new byte[0], "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(photoStorage);
    }

    @Test
    void addPhoto_throwsNotFound_whenProfileMissing() {
        ProfileId id = ProfileId.generate();
        when(profileRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addPhoto(id, new byte[10], "image/jpeg"))
                .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void addPhoto_deletesStoredObject_whenProfileSaveFails() {
        Profile profile = profileWithPhotos(0);
        when(profileRepository.findById(profile.id())).thenReturn(Optional.of(profile));
        when(photoStorage.store(any(), anyString())).thenReturn("profile-photos/orphan.jpg");
        when(profileRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.addPhoto(profile.id(), new byte[10], "image/jpeg"))
                .isInstanceOf(IllegalStateException.class);
        verify(photoStorage).delete("profile-photos/orphan.jpg");
    }

    @Test
    void removePhoto_removesUrnFromProfileAndStorage() {
        Profile profile = profileWithPhotos(2);
        String urn = profile.photoUrns().get(0);
        when(profileRepository.findById(profile.id())).thenReturn(Optional.of(profile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Profile result = service.removePhoto(profile.id(), urn);

        assertThat(result.photoUrns()).doesNotContain(urn).hasSize(1);
        verify(photoStorage).delete(urn);
    }

    @Test
    void removePhoto_rejectsUrnNotOwnedByProfile() {
        Profile profile = profileWithPhotos(1);
        when(profileRepository.findById(profile.id())).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.removePhoto(profile.id(), "profile-photos/other.jpg"))
                .isInstanceOf(PhotoNotFoundException.class);
        verify(photoStorage, never()).delete(anyString());
    }

    @Test
    void getPhoto_returnsBytes_forOwnedUrn_andRejectsForeignUrn() {
        Profile profile = profileWithPhotos(1);
        String urn = profile.photoUrns().get(0);
        when(profileRepository.findById(profile.id())).thenReturn(Optional.of(profile));
        when(photoStorage.retrieve(urn)).thenReturn(new byte[] {1, 2, 3});

        assertThat(service.getPhoto(profile.id(), urn)).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> service.getPhoto(profile.id(), "profile-photos/foreign.jpg"))
                .isInstanceOf(PhotoNotFoundException.class);
    }

    private static Profile profileWithPhotos(int count) {
        List<String> urns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            urns.add("profile-photos/existing-" + i + ".jpg");
        }
        Instant now = Instant.now();
        return Profile.builder().id(ProfileId.generate()).telegramUserId("tg").displayName("Name").age(28)
                .gender(Gender.FEMALE).orientation(Orientation.STRAIGHT).country("US").city("NYC")
                .seekingGenders(Set.of(Gender.MALE)).selfDescription("self description text")
                .preferenceDescription("preference description text").embeddings(ProfileEmbeddings.empty())
                .createdAt(now).updatedAt(now).photoUrns(urns).build();
    }
}
