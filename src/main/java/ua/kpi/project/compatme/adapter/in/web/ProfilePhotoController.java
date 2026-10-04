package ua.kpi.project.compatme.adapter.in.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ua.kpi.project.compatme.adapter.in.web.dto.PhotoUploadResponse;
import ua.kpi.project.compatme.application.port.in.ProfilePhotoUseCase;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;

/**
 * Inbound REST adapter for profile photos. Performs no validation itself: size/type/count rules
 * live in {@link ProfilePhotoUseCase}, shared with every other upload path (e.g. the Telegram bot).
 */
@RestController
@RequestMapping("/api/v1/profiles/{profileId}/photos")
public class ProfilePhotoController {

    private final ProfilePhotoUseCase photoUseCase;

    public ProfilePhotoController(ProfilePhotoUseCase photoUseCase) {
        this.photoUseCase = photoUseCase;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoUploadResponse> upload(
            @PathVariable String profileId, @RequestParam("file") MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read uploaded file", e);
        }
        Profile updated = photoUseCase.addPhoto(ProfileId.of(profileId), bytes, file.getContentType());
        String newUrn = updated.photoUrns().get(updated.photoUrns().size() - 1);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PhotoUploadResponse(newUrn, updated.photoUrns()));
    }

    /** URNs contain a slash ({@code profile-photos/<uuid>.<ext>}), hence the catch-all path variable. */
    @GetMapping("/{*urn}")
    public ResponseEntity<byte[]> download(@PathVariable String profileId, @PathVariable String urn) {
        String key = stripLeadingSlash(urn);
        byte[] bytes = photoUseCase.getPhoto(ProfileId.of(profileId), key);
        return ResponseEntity.ok()
                .contentType(mediaTypeFor(key))
                .cacheControl(CacheControl.noStore())
                .body(bytes);
    }

    @DeleteMapping("/{*urn}")
    public ResponseEntity<Void> delete(@PathVariable String profileId, @PathVariable String urn) {
        photoUseCase.removePhoto(ProfileId.of(profileId), stripLeadingSlash(urn));
        return ResponseEntity.noContent().build();
    }

    private static String stripLeadingSlash(String urn) {
        return urn.startsWith("/") ? urn.substring(1) : urn;
    }

    private static MediaType mediaTypeFor(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
