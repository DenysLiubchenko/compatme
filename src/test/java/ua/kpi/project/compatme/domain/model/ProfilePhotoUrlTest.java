package ua.kpi.project.compatme.domain.model;

import org.junit.jupiter.api.Test;
import ua.kpi.project.compatme.domain.exception.InvalidProfileDataException;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers {@code Profile}'s optional {@code photoUrls} field: empty list is allowed (no photos),
 * {@code http://}/{@code https://} URLs are accepted as-is, and any other scheme (e.g.
 * {@code javascript:}, {@code data:}) is rejected — a lightweight guard against unsafe values
 * that would be dangerous if ever rendered directly in a client.
 */
class ProfilePhotoUrlTest {

    @Test
    void photoUrls_isEmpty_whenNotProvided() {
        Profile profile = profileWithPhotoUrls(null);

        assertThat(profile.photoUrls()).isEmpty();
    }

    @Test
    void photoUrls_isEmpty_whenEmpty() {
        Profile profile = profileWithPhotoUrls("");

        assertThat(profile.photoUrls()).isEmpty();
    }

    @Test
    void photoUrls_isAccepted_whenHttps() {
        Profile profile = profileWithPhotoUrls("https://example.com/photo.jpg");

        assertThat(profile.photoUrls()).containsExactly("https://example.com/photo.jpg");
    }

    @Test
    void photoUrls_isAccepted_whenHttp() {
        Profile profile = profileWithPhotoUrls("http://example.com/photo.jpg");

        assertThat(profile.photoUrls()).containsExactly("http://example.com/photo.jpg");
    }

    @Test
    void photoUrls_isRejected_whenSchemeIsNotHttpOrHttps() {
        assertThatThrownBy(() -> profileWithPhotoUrls("javascript:alert(1)"))
                .isInstanceOf(InvalidProfileDataException.class);
    }

    @Test
    void photoUrls_isRejected_whenNoScheme() {
        assertThatThrownBy(() -> profileWithPhotoUrls("example.com/photo.jpg"))
                .isInstanceOf(InvalidProfileDataException.class);
    }

    private static Profile profileWithPhotoUrls(String photoUrl) {
        Instant now = Instant.now();
        java.util.List<String> urls = photoUrl == null || photoUrl.isBlank() ? java.util.List.of() : java.util.List.of(photoUrl);
        return Profile.builder().id(ProfileId.generate()).displayName("name").age(25).gender(Gender.OTHER)
                .orientation(Orientation.OTHER).country("United States").city("New York").seekingGenders(Set.of())
                .selfDescription("self description").preferenceDescription("preference description")
                .embeddings(ProfileEmbeddings.empty()).createdAt(now).updatedAt(now).photoUrls(urls).build();
    }
}
