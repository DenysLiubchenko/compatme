package ua.kpi.project.compatme.domain.model;

import ua.kpi.project.compatme.domain.exception.InvalidProfileDataException;

/** Inclusive age range a user wants their matches to fall into (both bounds within 18..120). */
public record AgeRange(int min, int max) {

    public AgeRange {
        if (min < 18 || max > 120) {
            throw new InvalidProfileDataException("preferred age range must be within 18 and 120");
        }
        if (min > max) {
            throw new InvalidProfileDataException("minPreferredAge must not exceed maxPreferredAge");
        }
    }

    public boolean contains(int age) {
        return age >= min && age <= max;
    }
}
