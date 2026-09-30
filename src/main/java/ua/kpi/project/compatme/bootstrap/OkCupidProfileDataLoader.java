package ua.kpi.project.compatme.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.application.dto.CreateOrUpdateProfileCommand;
import ua.kpi.project.compatme.application.port.in.GenerateEmbeddingsUseCase;
import ua.kpi.project.compatme.application.port.in.ProfileManagementUseCase;
import ua.kpi.project.compatme.domain.model.DrinkingFrequency;
import ua.kpi.project.compatme.domain.model.DrugUseFrequency;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.OptionalProfileFields;
import ua.kpi.project.compatme.domain.model.Orientation;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;
import ua.kpi.project.compatme.domain.model.RelationshipStatus;
import ua.kpi.project.compatme.domain.model.SmokingStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Imports at most 10,000 CSV records that have both required descriptions. */
@Component
@ConditionalOnProperty(name = "app.okcupid.enabled", havingValue = "true")
public class OkCupidProfileDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OkCupidProfileDataLoader.class);
    private static final int MAX_PROFILES = 10_000;
    private static final DateTimeFormatter LAST_ONLINE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");

    private final ProfileManagementUseCase profileManagement;
    private final GenerateEmbeddingsUseCase embeddings;
    private final Resource csv;

    public OkCupidProfileDataLoader(ProfileManagementUseCase profileManagement, GenerateEmbeddingsUseCase embeddings,
                                    ResourceLoader resources) {
        this.profileManagement = profileManagement;
        this.embeddings = embeddings;
        this.csv = resources.getResource("classpath:okcupid_profiles.csv");
    }

    @Override
    public void run(String... args) throws Exception {
        int created = 0;
        int missingEssay = 0;
        int invalidRequired = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = readCsvRecord(reader);
            if (headerLine == null) throw new IllegalStateException("okcupid_profiles.csv is empty");
            List<String> header = parseCsvLine(headerLine);
            String line;
            long lineNumber = 1;
            while (created < MAX_PROFILES && (line = readCsvRecord(reader)) != null) {
                lineNumber++;
                List<String> values = parseCsvLine(line);
                if (values.size() != header.size()) {
                    invalidRequired++;
                    continue;
                }
                CsvProfile row = new CsvProfile(header, values);
                String about = row.get("essay0");
                String lookingFor = row.get("essay9");
                if (blank(about) || blank(lookingFor)) {
                    missingEssay++;
                    continue;
                }
                try {
                    String sampleKey = "okcupid-row-" + lineNumber;
                    CreateOrUpdateProfileCommand command = row.toCommand(sampleKey);
                    Profile profile = profileManagement.createOrUpdateProfile(command);
                    embeddings.generateEmbeddings(profile.id());
                    created++;
                } catch (RuntimeException e) {
                    invalidRequired++;
                    log.warn("Skipping OkCupid row {}: {}", lineNumber, e.getMessage());
                }
            }
        }
        log.info("OkCupid import complete: imported={}, skippedMissingEssay0Or9={}, skippedInvalid={}",
                created, missingEssay, invalidRequired);
    }

    private final class CsvProfile {
        private final List<String> header;
        private final List<String> values;

        private CsvProfile(List<String> header, List<String> values) { this.header = header; this.values = values; }
        private String get(String name) { int index = header.indexOf(name); return index < 0 ? "" : values.get(index).trim(); }

        private CreateOrUpdateProfileCommand toCommand(String sampleKey) {
            Gender gender = parseGender(get("sex"));
            Orientation orientation = parseOrientation(get("orientation"));
            LocationParts location = parseLocation(get("city"));
            String name = "OkCupid " + sampleKey;
            OptionalProfileFields optional = new OptionalProfileFields(
                    relationship(get("status")), get("body_type"), blankToNull(get("diet")),
                    drinking(get("drinks")), drugs(get("drugs")), blankToNull(get("education")),
                    splitValues(get("ethnicity")), decimal(get("height")), parseIncome(get("income")),
                    blankToNull(get("job")), lastOnline(get("last_online")), blankToNull(get("offspring")),
                    blankToNull(get("pets")), blankToNull(get("religion")), blankToNull(get("sign")),
                    smoking(get("smokes")), splitSpeaks(get("speaks")));
            Set<Gender> seeking = seekingFor(gender, orientation);
            return new CreateOrUpdateProfileCommand(
                    deterministicId(sampleKey), null, name, integer(get("age")), gender, orientation,
                    seeking, get("essay0"), get("essay9"), List.of(), location.country(), location.city(),
                    null, List.of(), optional, List.of());
        }
    }

    static String deterministicId(String key) {
        return UUID.nameUUIDFromBytes(("compatme-okcupid:" + key).getBytes(StandardCharsets.UTF_8)).toString();
    }

    static LocationParts parseLocation(String source) {
        if (blank(source)) return new LocationParts("United States", "Unknown");
        String city = source.split(",", 2)[0].trim().toLowerCase(Locale.ROOT);
        StringBuilder title = new StringBuilder();
        boolean capitalize = true;
        for (char c : city.toCharArray()) {
            title.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = Character.isWhitespace(c) || c == '-';
        }
        return new LocationParts("United States", title.toString());
    }

    record LocationParts(String country, String city) { }
    static Gender parseGender(String source) {
        return switch (source == null ? "" : source.trim().toLowerCase(Locale.ROOT)) {
            case "m" -> Gender.MALE;
            case "f" -> Gender.FEMALE;
            default -> null;
        };
    }
    static Orientation parseOrientation(String source) { return enumValue(Orientation.class, source); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String blankToNull(String s) { return blank(s) ? null : s; }
    private static Integer integer(String s) { try { return Integer.valueOf(s); } catch (Exception e) { return null; } }
    private static Double decimal(String s) { try { return Double.valueOf(s); } catch (Exception e) { return null; } }
    static Integer parseIncome(String s) { Integer n = integer(s); return n == null || n < 0 ? null : n; }
    private static Instant lastOnline(String s) {
        try { return LocalDateTime.parse(s, LAST_ONLINE_FORMAT).toInstant(ZoneOffset.UTC); } catch (Exception e) { return null; }
    }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        if (blank(value)) return null;
        String normalized = value.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
        try { return Enum.valueOf(type, normalized); } catch (IllegalArgumentException e) { return type.getEnumConstants()[type.getEnumConstants().length - 1]; }
    }
    private static RelationshipStatus relationship(String value) {
        if (blank(value)) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "single" -> RelationshipStatus.SINGLE;
            case "available" -> RelationshipStatus.AVAILABLE;
            case "seeing someone" -> RelationshipStatus.SEEING_SOMEONE;
            case "married" -> RelationshipStatus.MARRIED;
            case "unknown" -> RelationshipStatus.UNKNOWN;
            default -> RelationshipStatus.OTHER;
        };
    }
    private static DrinkingFrequency drinking(String value) {
        if (blank(value)) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "not at all" -> DrinkingFrequency.NOT_AT_ALL;
            case "rarely" -> DrinkingFrequency.RARELY;
            case "socially" -> DrinkingFrequency.SOCIALLY;
            case "often" -> DrinkingFrequency.OFTEN;
            case "very often" -> DrinkingFrequency.VERY_OFTEN;
            case "desperately" -> DrinkingFrequency.DESPERATELY;
            default -> DrinkingFrequency.OTHER;
        };
    }
    private static DrugUseFrequency drugs(String value) {
        if (blank(value)) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "never" -> DrugUseFrequency.NEVER;
            case "sometimes" -> DrugUseFrequency.SOMETIMES;
            case "often" -> DrugUseFrequency.OFTEN;
            default -> DrugUseFrequency.OTHER;
        };
    }
    private static SmokingStatus smoking(String value) {
        if (blank(value)) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "no" -> SmokingStatus.NO;
            case "sometimes" -> SmokingStatus.SOMETIMES;
            case "when drinking" -> SmokingStatus.WHEN_DRINKING;
            case "yes" -> SmokingStatus.YES;
            case "trying to quit" -> SmokingStatus.TRYING_TO_QUIT;
            default -> SmokingStatus.OTHER;
        };
    }
    private static List<String> splitValues(String value) {
        if (blank(value)) return List.of();
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) if (!part.isBlank()) result.add(part.trim());
        return List.copyOf(result);
    }
    private static List<String> splitSpeaks(String value) { return splitValues(value.replaceAll("\\([^)]*\\)", "")); }

    /** RFC 4180 record parser supporting quoted commas, doubled quotes and escaped CR/LF. */
    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { value.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) {
                fields.add(value.toString()); value.setLength(0);
            } else value.append(c);
        }
        fields.add(value.toString());
        return fields;
    }

    static String readCsvRecord(BufferedReader reader) throws Exception {
        StringBuilder record = new StringBuilder();
        boolean quoted = false;
        int code;
        while ((code = reader.read()) != -1) {
            char c = (char) code;
            if (c == '"') {
                record.append(c);
                if (quoted) {
                    reader.mark(1);
                    int next = reader.read();
                    if (next == '"') record.append('"');
                    else {
                        quoted = false;
                        if (next != -1) reader.reset();
                    }
                } else {
                    quoted = true;
                }
            } else if ((c == '\n' || c == '\r') && !quoted) {
                if (c == '\r') {
                    reader.mark(1);
                    int next = reader.read();
                    if (next != '\n' && next != -1) reader.reset();
                }
                return record.toString();
            } else {
                record.append(c);
            }
        }
        return record.isEmpty() ? null : record.toString();
    }

    private static Set<Gender> seekingFor(Gender gender, Orientation orientation) {
        if (orientation == Orientation.STRAIGHT) {
            return gender == Gender.MALE ? Set.of(Gender.FEMALE) : Set.of(Gender.MALE);
        }
        if (orientation == Orientation.GAY) {
            return gender == Gender.MALE ? Set.of(Gender.MALE) : Set.of(Gender.FEMALE);
        }
        return Set.of(Gender.MALE, Gender.FEMALE, Gender.NON_BINARY, Gender.OTHER);
    }
}
