package ua.kpi.project.compatme.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a {@link Profile}, modeled as a value object so the domain never depends on
 * MongoDB's {@code ObjectId} or any other persistence-specific identifier type.
 */
public final class ProfileId {

    private final String value;

    private ProfileId(String value) {
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public static ProfileId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ProfileId value must not be blank");
        }
        return new ProfileId(value);
    }

    public static ProfileId generate() {
        return new ProfileId(UUID.randomUUID().toString());
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProfileId profileId)) {
            return false;
        }
        return value.equals(profileId.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
