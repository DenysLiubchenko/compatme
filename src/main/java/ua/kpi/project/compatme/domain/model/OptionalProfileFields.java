package ua.kpi.project.compatme.domain.model;

import java.time.Instant;
import java.util.List;

/** Nullable descriptive attributes extracted from profile descriptions; never used for scoring. */
public record OptionalProfileFields(
        RelationshipStatus status,
        String bodyType,
        String diet,
        DrinkingFrequency drinks,
        DrugUseFrequency drugs,
        String education,
        List<String> ethnicity,
        Double height,
        Integer income,
        String job,
        Instant lastOnline,
        String offspring,
        String pets,
        String religion,
        String sign,
        SmokingStatus smokes,
        List<String> speaks) {

    public static OptionalProfileFields empty() {
        return new OptionalProfileFields(null, null, null, null, null, null, List.of(), null, null,
                null, null, null, null, null, null, null, List.of());
    }
}
