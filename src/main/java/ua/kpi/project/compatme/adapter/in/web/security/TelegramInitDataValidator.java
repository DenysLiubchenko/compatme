package ua.kpi.project.compatme.adapter.in.web.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * Verifies Telegram Mini App {@code initData} as specified by Telegram: the data-check-string
 * (all fields except {@code hash}, sorted, joined by newlines) must match
 * {@code HMAC_SHA256(HMAC_SHA256("WebAppData", botToken), dataCheckString)}. Also enforces a
 * maximum {@code auth_date} age to prevent replay of captured payloads. Fails closed: with no bot
 * token configured, nothing validates.
 */
public class TelegramInitDataValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Duration ALLOWED_CLOCK_SKEW = Duration.ofMinutes(1);

    private final String botToken;
    private final Duration maxAge;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelegramInitDataValidator(String botToken, Duration maxAge, Clock clock) {
        this.botToken = botToken;
        this.maxAge = maxAge;
        this.clock = clock;
    }

    /** Returns the verified Telegram user id, or throws {@link InvalidTelegramInitDataException}. */
    public String validateAndExtractUserId(String initData) {
        if (botToken == null || botToken.isBlank()) {
            throw new InvalidTelegramInitDataException("Telegram authentication is not configured");
        }
        if (initData == null || initData.isBlank()) {
            throw new InvalidTelegramInitDataException("initData is missing");
        }

        Map<String, String> fields = parse(initData);
        String receivedHash = fields.remove("hash");
        if (receivedHash == null || receivedHash.isBlank()) {
            throw new InvalidTelegramInitDataException("initData hash is missing");
        }

        StringBuilder dataCheckString = new StringBuilder();
        fields.forEach((key, value) -> {
            if (!dataCheckString.isEmpty()) {
                dataCheckString.append('\n');
            }
            dataCheckString.append(key).append('=').append(value);
        });

        String expectedHash = HexFormat.of().formatHex(hmac(
                hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8)),
                dataCheckString.toString().getBytes(StandardCharsets.UTF_8)));
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                receivedHash.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidTelegramInitDataException("initData signature is invalid");
        }

        checkFresh(fields.get("auth_date"));
        return extractUserId(fields.get("user"));
    }

    private void checkFresh(String authDate) {
        Instant issuedAt;
        try {
            issuedAt = Instant.ofEpochSecond(Long.parseLong(authDate));
        } catch (NumberFormatException | NullPointerException e) {
            throw new InvalidTelegramInitDataException("initData auth_date is missing or invalid");
        }
        Instant now = clock.instant();
        if (issuedAt.isBefore(now.minus(maxAge)) || issuedAt.isAfter(now.plus(ALLOWED_CLOCK_SKEW))) {
            throw new InvalidTelegramInitDataException("initData has expired");
        }
    }

    private String extractUserId(String userJson) {
        if (userJson == null) {
            throw new InvalidTelegramInitDataException("initData does not contain a user");
        }
        try {
            JsonNode id = objectMapper.readTree(userJson).get("id");
            if (id == null || !id.canConvertToLong()) {
                throw new InvalidTelegramInitDataException("initData user id is missing");
            }
            return Long.toString(id.asLong());
        } catch (InvalidTelegramInitDataException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidTelegramInitDataException("initData user is malformed");
        }
    }

    private static Map<String, String> parse(String initData) {
        Map<String, String> fields = new TreeMap<>();
        for (String pair : initData.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                throw new InvalidTelegramInitDataException("initData is malformed");
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            if (fields.put(key, value) != null) {
                throw new InvalidTelegramInitDataException("initData contains duplicate fields");
            }
        }
        return fields;
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }
}
