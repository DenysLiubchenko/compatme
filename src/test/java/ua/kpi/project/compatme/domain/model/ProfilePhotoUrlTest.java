package ua.kpi.project.compatme.domain.model;

import org.junit.jupiter.api.Test;
import ua.kpi.project.compatme.domain.exception.InvalidProfileDataException;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers {@code Profile}'s optional {@code photoUrl} field: null/blank is allowed (no photo),
 * {@code http://}/{@code https://} URLs are accepted as-is, and any other scheme (e.g.
 * {@code javascript:}, {@code data:}) is rejected — a lightweight guard against unsafe values
 * that would be dangerous if ever rendered directly in a client.
 */
class ProfilePhotoUrlTest {

    @Test
    void photoUrl_isNull_whenNotProvided() {
        Profile profile = profileWithPhotoUrl(null);

        assertThat(profile.photoUrl()).isNull();
    }

    @Test
    void photoUrl_isNull_whenBlank() {
        Profile profile = profileWithPhotoUrl("   ");

        assertThat(profile.photoUrl()).isNull();
    }

    @Test
    void photoUrl_isAccepted_whenHttps() {
        Profile profile = profileWithPhotoUrl("https://example.com/photo.jpg");

        assertThat(profile.photoUrl()).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    void photoUrl_isAccepted_whenHttp() {
        Profile profile = profileWithPhotoUrl("http://example.com/photo.jpg");

        assertThat(profile.photoUrl()).isEqualTo("http://example.com/photo.jpg");
    }

    @Test
    void photoUrl_isRejected_whenSchemeIsNotHttpOrHttps() {
        assertThatThrownBy(() -> profileWithPhotoUrl("javascript:alert(1)"))
                .isInstanceOf(InvalidProfileDataException.class);
    }

    @Test
    void photoUrl_isRejected_whenNoScheme() {
        assertThatThrownBy(() -> profileWithPhotoUrl("example.com/photo.jpg"))
                .isInstanceOf(InvalidProfileDataException.class);
    }

    private static Profile profileWithPhotoUrl(String photoUrl) {
        Instant now = Instant.now();
        return new Profile(
                ProfileId.generate(), null, "name", 25, Gender.OTHER, Set.of(),
                "self description", "preference description", ProfileEmbeddings.empty(),
                now, now, null, null, null, photoUrl);
    }
}
