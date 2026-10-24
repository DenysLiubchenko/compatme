package ua.kpi.project.compatme.adapter.in.web.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramInitDataValidatorTest {

    private static final String BOT_TOKEN = "123456:TEST-BOT-TOKEN";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final TelegramInitDataValidator validator =
            new TelegramInitDataValidator(BOT_TOKEN, Duration.ofHours(1), CLOCK);

    @Test
    void validInitData_returnsVerifiedUserId() {
        String initData = signed(BOT_TOKEN, NOW.minusSeconds(60), "{\"id\":4242,\"first_name\":\"Ann\"}");

        assertThat(validator.validateAndExtractUserId(initData)).isEqualTo("4242");
    }

    @Test
    void tamperedUser_isRejected() {
        String initData = signed(BOT_TOKEN, NOW.minusSeconds(60), "{\"id\":4242}")
                .replace(URLEncoder.encode("4242", StandardCharsets.UTF_8), "9999");

        assertThatThrownBy(() -> validator.validateAndExtractUserId(initData))
                .isInstanceOf(InvalidTelegramInitDataException.class);
    }

    @Test
    void signedWithDifferentBotToken_isRejected() {
        String initData = signed("999:OTHER-BOT", NOW.minusSeconds(60), "{\"id\":4242}");

        assertThatThrownBy(() -> validator.validateAndExtractUserId(initData))
                .isInstanceOf(InvalidTelegramInitDataException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void expiredInitData_isRejected() {
        String initData = signed(BOT_TOKEN, NOW.minus(Duration.ofHours(2)), "{\"id\":4242}");

        assertThatThrownBy(() -> validator.validateAndExtractUserId(initData))
                .isInstanceOf(InvalidTelegramInitDataException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void missingHash_isRejected() {
        assertThatThrownBy(() -> validator.validateAndExtractUserId("auth_date=1&user=%7B%22id%22%3A1%7D"))
                .isInstanceOf(InvalidTelegramInitDataException.class);
    }

    @Test
    void blankOrMalformedInput_isRejected() {
        assertThatThrownBy(() -> validator.validateAndExtractUserId(" "))
                .isInstanceOf(InvalidTelegramInitDataException.class);
        assertThatThrownBy(() -> validator.validateAndExtractUserId("garbage"))
                .isInstanceOf(InvalidTelegramInitDataException.class);
    }

    @Test
    void noBotTokenConfigured_failsClosed() {
        var unconfigured = new TelegramInitDataValidator("", Duration.ofHours(1), CLOCK);
        String initData = signed("", NOW.minusSeconds(1), "{\"id\":1}");

        assertThatThrownBy(() -> unconfigured.validateAndExtractUserId(initData))
                .isInstanceOf(InvalidTelegramInitDataException.class);
    }

    /** Builds initData exactly the way Telegram does, so the test is an independent oracle. */
    static String signed(String botToken, Instant authDate, String userJson) {
        Map<String, String> fields = new TreeMap<>();
        fields.put("auth_date", Long.toString(authDate.getEpochSecond()));
        fields.put("query_id", "AAH");
        fields.put("user", userJson);
        String checkString = fields.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n"));
        byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8));
        String hash = HexFormat.of().formatHex(hmac(secret, checkString.getBytes(StandardCharsets.UTF_8)));
        fields.put("hash", hash);
        return fields.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.length == 0 ? new byte[] {0} : key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
