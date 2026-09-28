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

    private final String country;
    private final String city;

    /**
     * Optional URL of the profile's photo. We store only the URL — never the image bytes
     * themselves — so hosting/CDN choice is entirely up to the caller. Validated to start with
     * {@code http://} or {@code https://} when present, rejecting other schemes (e.g.
     * {@code javascript:}, {@code data:}) that would be unsafe if ever rendered directly in a
     * client.
     */
    private final String photoUrl;

    /**
     * Telegram {@code file_id} references for up to 5 photos uploaded during onboarding/editing
     * via the Telegram bot. We store only these opaque ids — never the image bytes — since
     * Telegram itself hosts the files indefinitely and a {@code file_id} can be resent via
     * {@code sendPhoto}/{@code sendMediaGroup} at any time; this keeps MongoDB storage minimal.
     * Purely presentation data: like {@link #archetypeIds}, NEVER read by
     * {@link ua.kpi.project.compatme.domain.service.CompatibilityScorer} or any
     * {@link ua.kpi.project.compatme.domain.service.CompatibilityAggregationStrategy}.
     */
    private final List<String> photoFileIds;

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
        this(id, telegramUserId, displayName, age, gender, seekingGenders, selfDescription,
                preferenceDescription, embeddings, createdAt, updatedAt, archetypeIds, null, null);
    }

    /**
     * Full constructor, additionally accepting {@code country}/{@code city} — both optional
     * free-text location fields used only for the {@link LocationScope#COUNTRY}/
     * {@link LocationScope#CITY} recommendation filters (see {@link #matchesLocationScope}).
     * Comparisons against these fields are case-insensitive exact-string matches; no geocoding or
     * normalization is performed, which is an intentional simplicity tradeoff for thesis-prototype
     * scope (callers should keep spelling consistent, e.g. always "Kyiv", not "Kyiv"/"Kiev" mixed).
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
            List<Integer> archetypeIds,
            String country,
            String city) {
        this(id, telegramUserId, displayName, age, gender, seekingGenders, selfDescription,
                preferenceDescription, embeddings, createdAt, updatedAt, archetypeIds, country, city, null);
    }

    /** Full constructor, additionally accepting the optional {@code photoUrl} (see field Javadoc). */
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
            List<Integer> archetypeIds,
            String country,
            String city,
            String photoUrl) {
        this(id, telegramUserId, displayName, age, gender, seekingGenders, selfDescription,
                preferenceDescription, embeddings, createdAt, updatedAt, archetypeIds, country, city, photoUrl, null);
    }

    /** Full constructor, additionally accepting {@code photoFileIds} (see field Javadoc). */
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
            List<Integer> archetypeIds,
            String country,
            String city,
            String photoUrl,
            List<String> photoFileIds) {
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
        this.country = country;
        this.city = city;
        this.photoUrl = requireValidPhotoUrlOrNull(photoUrl);
        this.photoFileIds = photoFileIds == null || photoFileIds.isEmpty()
                ? List.of()
                : Collections.unmodifiableList(photoFileIds);
    }

    private static String requireValidPhotoUrlOrNull(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return null;
        }
        if (!photoUrl.startsWith("http://") && !photoUrl.startsWith("https://")) {
            throw new InvalidProfileDataException("photoUrl must start with http:// or https://, was: " + photoUrl);
        }
        return photoUrl;
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
                newSelfDescription, preferenceDescription, nextEmbeddings, createdAt, now, archetypeIds, country, city, photoUrl, photoFileIds);
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
                selfDescription, newPreferenceDescription, nextEmbeddings, createdAt, now, archetypeIds, country, city, photoUrl, photoFileIds);
    }

    public Profile withEmbeddings(ProfileEmbeddings newEmbeddings, Instant now) {
        return new Profile(id, telegramUserId, displayName, age, gender, seekingGenders,
                selfDescription, preferenceDescription, newEmbeddings, createdAt, now, archetypeIds, country, city, photoUrl, photoFileIds);
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

    /**
     * Location pre-filter for recommendations: does {@code candidate} fall within the requested
     * {@link LocationScope} relative to {@code this} profile's own {@code country}/{@code city}?
     * Comparisons are case-insensitive exact-string matches on {@code this} profile's location.
     *
     * <p>Permissive by design, matching {@link #matchesSeekingGender}: if {@code this} profile
     * hasn't set the field(s) the requested scope needs (e.g. {@code COUNTRY} scope but no
     * {@code country} set), the filter passes everyone rather than excluding every candidate —
     * an unset location on the requester's side means "no location preference", not "match
     * nobody".
     */
    public boolean matchesLocationScope(Profile candidate, LocationScope scope) {
        return switch (scope) {
            case GLOBAL -> true;
            case COUNTRY -> matchesLocationField(this.country, candidate.country);
            case CITY -> matchesLocationField(this.country, candidate.country) && matchesLocationField(this.city, candidate.city);
        };
    }

    private static boolean matchesLocationField(String requesterValue, String candidateValue) {
        if (requesterValue == null || requesterValue.isBlank()) {
            return true;
        }
        return requesterValue.equalsIgnoreCase(candidateValue);
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

    /** Optional free-text country, used only by the {@link LocationScope} recommendation filter. */
    public String country() {
        return country;
    }

    /** Optional free-text city, used only by the {@link LocationScope} recommendation filter. */
    public String city() {
        return city;
    }

    /** Optional photo URL (see field Javadoc for the {@code http(s)://}-only validation rule). */
    public String photoUrl() {
        return photoUrl;
    }

    /**
     * Telegram {@code file_id} references for this profile's uploaded photos (see field
     * Javadoc). Presentation-only — never read by scoring logic.
     */
    public List<String> photoFileIds() {
        return photoFileIds;
    }
}
