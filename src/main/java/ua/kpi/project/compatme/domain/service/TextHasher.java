package ua.kpi.project.compatme.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes a stable SHA-256 hash of profile description text, used to detect whether the
 * underlying text has changed since an embedding was last cached — so the system never
 * re-embeds unchanged text (requirement: embeddings are computed once and cached).
 *
 * <p>Explicitly hashes the UTF-8 byte representation so Ukrainian (Cyrillic) text is handled
 * correctly and deterministically, independent of platform default charset.
 */
public final class TextHasher {

    private TextHasher() {
    }

    public static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every standard JVM; this is unreachable.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
