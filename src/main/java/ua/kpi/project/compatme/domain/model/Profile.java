package ua.kpi.project.compatme.domain.model;

import ua.kpi.project.compatme.domain.exception.InvalidProfileDataException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Core domain aggregate: a user's dating profile.
 *
 * <p>Deliberately free of any Spring or MongoDB annotations — this class must remain plain Java
 * so the compatibility scoring logic can be unit-tested without a framework context, and so
 * persistence concerns can change (e.g. swapping MongoDB for another store) without touching this
 * class. Mapping to/from the MongoDB document representation happens exclusively in
 * {@code adapter.out.persistence}.
 *
 * <p>Instances are immutable; mutating operations return a new {@code Profile}.
 */
public final class Profile {

    private final ProfileId id;
    private final String telegramUserId;
    private final String displayName;
    private final Integer age;
    private final Gender gender;
    private final Set<Gender> seekingGenders;
    private final String selfDescription;
    private final String preferenceDescription;
    private final ProfileEmbeddings embeddings;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Thesis-evaluation-only metadata: which synthetic personality archetype(s) this profile
     * blends, tagged in the synthetic dataset generator. Purely descriptive — NEVER read by
     * {@link ua.kpi.project.compatme.domain.service.CompatibilityScorer}, any
     * {@link ua.kpi.project.compatme.domain.service.CompatibilityAggregationStrategy}, or the
     * recommendation candidate-filtering logic. Do not wire this into scoring.
     */
    private final List<Integer> archetypeIds;

    public Profile(
            ProfileId id,
            String telegramUserId,
            String displayName,
            Integer age,
            Gender gender,
            Set<Gender> seekingGenders,
            String selfDescription,
            String preferenceDescription,
            ProfileEmbeddings embeddings,
            Instant createdAt,
            Instant updatedAt) {
        this(id, telegramUserId, displayName, age, gender, seekingGenders, selfDescription,
                preferenceDescription, embeddings, createdAt, updatedAt, null);
    }

    /**
     * Full constructor, additionally accepting {@code archetypeIds} — thesis-evaluation-only
     * metadata (see the field Javadoc). The shorter constructor above defaults it to empty and
     * remains available for all call sites that don't care about archetype tagging.
     */
    public Profile(
            ProfileId id,
            String telegramUserId,
            String displayName,
            Integer age,
            Gender gender,
            Set<Gender> seekingGenders,
            String selfDescription,
            String preferenceDescription,
            ProfileEmbeddings embeddings,
            Instant createdAt,
            Instant updatedAt,
            List<Integer> archetypeIds) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.telegramUserId = telegramUserId;
        this.displayName = requireNonBlank(displayName, "displayName");
        this.age = requireValidAge(age);
        this.gender = gender;
        this.seekingGenders = seekingGenders == null || seekingGenders.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(seekingGenders);
        this.selfDescription = requireNonBlank(selfDescription, "selfDescription");
        this.preferenceDescription = requireNonBlank(preferenceDescription, "preferenceDescription");
        this.embeddings = embeddings == null ? ProfileEmbeddings.empty() : embeddings;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.archetypeIds = archetypeIds == null || archetypeIds.isEmpty()
                ? List.of()
                : Collections.unmodifiableList(archetypeIds);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidProfileDataException(fieldName + " must not be blank");
        }
        return value;
    }

    private static Integer requireValidAge(Integer age) {
        if (age != null && (age < 18 || age > 120)) {
            throw new InvalidProfileDataException("age must be between 18 and 120, was: " + age);
        }
        return age;
    }

    /**
     * Returns a copy of this profile with an updated {@code selfDescription}. If the text is
     * unchanged, the copy is otherwise identical (embedding staleness is determined by the
     * application layer comparing text hashes, not by this method).
     */
    public Profile withSelfDescription(String newSelfDescription, Instant now) {
        boolean textChanged = !this.selfDescription.equals(newSelfDescription);
        ProfileEmbeddings nextEmbeddings = textChanged
                ? embeddings.withSelfEmbedding(null)
                : embeddings;
        return new Profile(id, telegramUserId, displayName, age, gender, seekingGenders,
                newSelfDescription, preferenceDescription, nextEmbeddings, createdAt, now, archetypeIds);
    }

    /**
     * Returns a copy of this profile with an updated {@code preferenceDescription}. Used both by
     * direct profile edits and by the preference-refinement use case (natural-language dialogue).
     */
    public Profile withPreferenceDescription(String newPreferenceDescription, Instant now) {
        boolean textChanged = !this.preferenceDescription.equals(newPreferenceDescription);
        ProfileEmbeddings nextEmbeddings = textChanged
                ? embeddings.withPreferenceEmbedding(null)
                : embeddings;
        return new Profile(id, telegramUserId, displayName, age, gender, seekingGenders,
                selfDescription, newPreferenceDescription, nextEmbeddings, createdAt, now, archetypeIds);
    }

    public Profile withEmbeddings(ProfileEmbeddings newEmbeddings, Instant now) {
        return new Profile(id, telegramUserId, displayName, age, gender, seekingGenders,
                selfDescription, preferenceDescription, newEmbeddings, createdAt, now, archetypeIds);
    }

    /** Hard pre-filter: does this candidate's declared gender fall within what {@code this} is seeking? */
    public boolean matchesSeekingGender(Profile candidate) {
        if (seekingGenders.isEmpty() || candidate.gender == null) {
            return true;
        }
        return seekingGenders.contains(candidate.gender);
    }

    /**
     * Reciprocal gender-preference check: {@code true} only if each profile's declared gender
     * falls within the other's {@code seekingGenders} (or the other side has no preference at
     * all). Handles multi-gender {@code seekingGenders} correctly on either or both sides, since
     * {@link #matchesSeekingGender(Profile)} is a set-membership check, not an exact-value check.
     */
    public boolean mutuallyMatchesSeekingGender(Profile other) {
        return this.matchesSeekingGender(other) && other.matchesSeekingGender(this);
    }

    public ProfileId id() {
        return id;
    }

    public String telegramUserId() {
        return telegramUserId;
    }

    public String displayName() {
        return displayName;
    }

    public Integer age() {
        return age;
    }

    public Gender gender() {
        return gender;
    }

    public Set<Gender> seekingGenders() {
        return seekingGenders;
    }

    public String selfDescription() {
        return selfDescription;
    }

    public String preferenceDescription() {
        return preferenceDescription;
    }

    public ProfileEmbeddings embeddings() {
        return embeddings;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Thesis-evaluation-only metadata (synthetic archetype tags). Never read by scoring logic —
     * see the field Javadoc above.
     */
    public List<Integer> archetypeIds() {
        return archetypeIds;
    }
}
