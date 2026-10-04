package ua.kpi.project.compatme.domain.model;

import ua.kpi.project.compatme.domain.exception.InvalidProfileDataException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable profile aggregate. Only {@code selfDescription} and {@code preferenceDescription}
 * are embedding/scoring inputs. Demographics, location, archetype tags, and photo references are
 * descriptive/filtering data and are never read by {@code CompatibilityScorer}.
 *
 * <p>This class intentionally has no framework or persistence annotations. Construct profiles
 * with {@link #builder()} to avoid positional-argument mistakes as the schema evolves.
 */
public final class Profile {

    private final ProfileId id;
    private final String telegramUserId;
    private final String displayName;
    private final Integer age;
    private final Gender gender;
    private final Orientation orientation;
    private final Set<Gender> seekingGenders;
    private final String selfDescription;
    private final String preferenceDescription;
    private final ProfileEmbeddings embeddings;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final List<Integer> archetypeIds;
    private final Location location;
    private final LocationScope searchScope;
    private final AgeRange ageRange;
    private final List<String> photoUrls;
    private final RelationshipStatus status;
    private final String bodyType;
    private final String diet;
    private final DrinkingFrequency drinks;
    private final DrugUseFrequency drugs;
    private final String education;
    private final List<String> ethnicity;
    private final Double height;
    private final Integer income;
    private final String job;
    private final Instant lastOnline;
    private final String offspring;
    private final String pets;
    private final String religion;
    private final String sign;
    private final SmokingStatus smokes;
    private final List<String> speaks;
    private final List<String> photoUrns;

    private Profile(Builder builder) {
        id = Objects.requireNonNull(builder.id, "id must not be null");
        displayName = requireNonBlank(builder.displayName, "name");
        age = requireValidAge(builder.age);
        gender = Objects.requireNonNull(builder.gender, "sex must not be null");
        orientation = Objects.requireNonNull(builder.orientation, "orientation must not be null");
        location = builder.location != null
                ? builder.location
                : new Location(builder.city, builder.country);
        searchScope = builder.searchScope == null ? LocationScope.WORLDWIDE : builder.searchScope;
        if ((builder.minPreferredAge == null) != (builder.maxPreferredAge == null)) {
            throw new InvalidProfileDataException("minPreferredAge and maxPreferredAge must be provided together");
        }
        ageRange = builder.minPreferredAge == null
                ? null : new AgeRange(builder.minPreferredAge, builder.maxPreferredAge);
        selfDescription = requireNonBlank(builder.selfDescription, "selfDescription");
        preferenceDescription = requireNonBlank(builder.preferenceDescription, "preferenceDescription");
        telegramUserId = builder.telegramUserId;
        seekingGenders = builder.seekingGenders == null || builder.seekingGenders.isEmpty()
                ? Set.of()
                : Set.copyOf(builder.seekingGenders);
        embeddings = builder.embeddings == null ? ProfileEmbeddings.empty() : builder.embeddings;
        createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt must not be null");
        archetypeIds = immutableList(builder.archetypeIds);
        photoUrls = validateAndCopyPhotoUrls(builder.photoUrls);
        status = builder.status;
        bodyType = builder.bodyType;
        diet = builder.diet;
        drinks = builder.drinks;
        drugs = builder.drugs;
        education = builder.education;
        ethnicity = immutableList(builder.ethnicity);
        height = builder.height;
        income = builder.income;
        job = builder.job;
        lastOnline = builder.lastOnline;
        offspring = builder.offspring;
        pets = builder.pets;
        religion = builder.religion;
        sign = builder.sign;
        smokes = builder.smokes;
        speaks = immutableList(builder.speaks);
        photoUrns = immutableList(builder.photoUrns);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null || values.isEmpty() ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidProfileDataException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static Integer requireValidAge(Integer age) {
        if (age == null || age < 18 || age > 120) {
            throw new InvalidProfileDataException("age must be between 18 and 120");
        }
        return age;
    }

    private static String requireValidPhotoUrlOrNull(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) {
            return null;
        }
        if (!photoUrl.startsWith("http://") && !photoUrl.startsWith("https://")) {
            throw new InvalidProfileDataException("photoUrl must start with http:// or https://");
        }
        return photoUrl;
    }

    private static List<String> validateAndCopyPhotoUrls(List<String> photoUrls) {
        if (photoUrls == null || photoUrls.isEmpty()) {
            return List.of();
        }
        for (String url : photoUrls) {
            if (url != null && !url.isBlank()) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    throw new InvalidProfileDataException("Each photoUrl must start with http:// or https://");
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(photoUrls));
    }

    public Profile withSelfDescription(String newSelfDescription, Instant now) {
        ProfileEmbeddings next = selfDescription.equals(newSelfDescription)
                ? embeddings
                : embeddings.withSelfEmbedding(null);
        return toBuilder().selfDescription(newSelfDescription).embeddings(next).updatedAt(now).build();
    }

    public Profile withPreferenceDescription(String newPreferenceDescription, Instant now) {
        ProfileEmbeddings next = preferenceDescription.equals(newPreferenceDescription)
                ? embeddings
                : embeddings.withPreferenceEmbedding(null);
        return toBuilder().preferenceDescription(newPreferenceDescription).embeddings(next).updatedAt(now).build();
    }

    public Profile withEmbeddings(ProfileEmbeddings newEmbeddings, Instant now) {
        return toBuilder().embeddings(newEmbeddings).updatedAt(now).build();
    }

    public Profile withPhotoUrns(List<String> newPhotoUrns, Instant now) {
        return toBuilder().photoUrns(newPhotoUrns).updatedAt(now).build();
    }

    private Builder toBuilder() {
        return builder().id(id).telegramUserId(telegramUserId).displayName(displayName).age(age).gender(gender)
                .orientation(orientation).seekingGenders(seekingGenders)
                .selfDescription(selfDescription).preferenceDescription(preferenceDescription)
                .embeddings(embeddings).createdAt(createdAt).updatedAt(updatedAt).archetypeIds(archetypeIds)
                .location(location).searchScope(searchScope)
                .minPreferredAge(minPreferredAge()).maxPreferredAge(maxPreferredAge()).photoUrls(photoUrls).status(status)
                .bodyType(bodyType).diet(diet).drinks(drinks).drugs(drugs).education(education)
                .ethnicity(ethnicity).height(height).income(income).job(job).lastOnline(lastOnline)
                .offspring(offspring).pets(pets).religion(religion).sign(sign).smokes(smokes)
                .speaks(speaks).photoUrns(photoUrns);
    }

    public boolean matchesSeekingGender(Profile candidate) {
        return seekingGenders.isEmpty() || candidate.gender == null || seekingGenders.contains(candidate.gender);
    }

    public boolean mutuallyMatchesSeekingGender(Profile other) {
        return matchesSeekingGender(other) && other.matchesSeekingGender(this);
    }

    public ProfileId id() { return id; }
    public String telegramUserId() { return telegramUserId; }
    public String displayName() { return displayName; }
    public Integer age() { return age; }
    public Gender gender() { return gender; }
    public Orientation orientation() { return orientation; }
    public Set<Gender> seekingGenders() { return seekingGenders; }
    public String selfDescription() { return selfDescription; }
    public String preferenceDescription() { return preferenceDescription; }
    public ProfileEmbeddings embeddings() { return embeddings; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public List<Integer> archetypeIds() { return archetypeIds; }
    public Location location() { return location; }
    /** Per-user default search scope; may be overridden per search request. */
    public LocationScope searchScope() { return searchScope; }
    /** Preferred age range of matches; {@code null} when the user hasn't set one. */
    public AgeRange ageRange() { return ageRange; }
    public Integer minPreferredAge() { return ageRange == null ? null : ageRange.min(); }
    public Integer maxPreferredAge() { return ageRange == null ? null : ageRange.max(); }
    public String country() { return location.country(); }
    public String city() { return location.city(); }
    public List<String> photoUrls() { return photoUrls; }
    public RelationshipStatus status() { return status; }
    public String bodyType() { return bodyType; }
    public String diet() { return diet; }
    public DrinkingFrequency drinks() { return drinks; }
    public DrugUseFrequency drugs() { return drugs; }
    public String education() { return education; }
    public List<String> ethnicity() { return ethnicity; }
    public Double height() { return height; }
    public Integer income() { return income; }
    public String job() { return job; }
    public Instant lastOnline() { return lastOnline; }
    public String offspring() { return offspring; }
    public String pets() { return pets; }
    public String religion() { return religion; }
    public String sign() { return sign; }
    public SmokingStatus smokes() { return smokes; }
    public List<String> speaks() { return speaks; }
    public List<String> photoUrns() { return photoUrns; }

    public static final class Builder {
        private ProfileId id;
        private String telegramUserId;
        private String displayName;
        private Integer age;
        private Gender gender;
        private Orientation orientation;
        private Set<Gender> seekingGenders;
        private String selfDescription;
        private String preferenceDescription;
        private ProfileEmbeddings embeddings;
        private Instant createdAt;
        private Instant updatedAt;
        private List<Integer> archetypeIds;
        private Location location;
        private LocationScope searchScope;
        private Integer minPreferredAge;
        private Integer maxPreferredAge;
        private String country;
        private String city;
        private List<String> photoUrls;
        private RelationshipStatus status;
        private String bodyType;
        private String diet;
        private DrinkingFrequency drinks;
        private DrugUseFrequency drugs;
        private String education;
        private List<String> ethnicity;
        private Double height;
        private Integer income;
        private String job;
        private Instant lastOnline;
        private String offspring;
        private String pets;
        private String religion;
        private String sign;
        private SmokingStatus smokes;
        private List<String> speaks;
        private List<String> photoUrns;

        private Builder() { }
        public Builder id(ProfileId v) { id = v; return this; }
        public Builder telegramUserId(String v) { telegramUserId = v; return this; }
        public Builder displayName(String v) { displayName = v; return this; }
        public Builder age(Integer v) { age = v; return this; }
        public Builder gender(Gender v) { gender = v; return this; }
        public Builder orientation(Orientation v) { orientation = v; return this; }
        public Builder seekingGenders(Set<Gender> v) { seekingGenders = v; return this; }
        public Builder selfDescription(String v) { selfDescription = v; return this; }
        public Builder preferenceDescription(String v) { preferenceDescription = v; return this; }
        public Builder embeddings(ProfileEmbeddings v) { embeddings = v; return this; }
        public Builder createdAt(Instant v) { createdAt = v; return this; }
        public Builder updatedAt(Instant v) { updatedAt = v; return this; }
        public Builder archetypeIds(List<Integer> v) { archetypeIds = v; return this; }
        public Builder minPreferredAge(Integer v) { minPreferredAge = v; return this; }
        public Builder maxPreferredAge(Integer v) { maxPreferredAge = v; return this; }
        public Builder searchScope(LocationScope v) { searchScope = v; return this; }
        public Builder location(Location v) { location = v; return this; }
        public Builder country(String v) { country = v; location = null; return this; }
        public Builder city(String v) { city = v; location = null; return this; }
        public Builder photoUrls(List<String> v) { photoUrls = v; return this; }
        public Builder status(RelationshipStatus v) { status = v; return this; }
        public Builder bodyType(String v) { bodyType = v; return this; }
        public Builder diet(String v) { diet = v; return this; }
        public Builder drinks(DrinkingFrequency v) { drinks = v; return this; }
        public Builder drugs(DrugUseFrequency v) { drugs = v; return this; }
        public Builder education(String v) { education = v; return this; }
        public Builder ethnicity(List<String> v) { ethnicity = v; return this; }
        public Builder height(Double v) { height = v; return this; }
        public Builder income(Integer v) { income = v; return this; }
        public Builder job(String v) { job = v; return this; }
        public Builder lastOnline(Instant v) { lastOnline = v; return this; }
        public Builder offspring(String v) { offspring = v; return this; }
        public Builder pets(String v) { pets = v; return this; }
        public Builder religion(String v) { religion = v; return this; }
        public Builder sign(String v) { sign = v; return this; }
        public Builder smokes(SmokingStatus v) { smokes = v; return this; }
        public Builder speaks(List<String> v) { speaks = v; return this; }
        public Builder photoUrns(List<String> v) { photoUrns = v; return this; }
        public Profile build() { return new Profile(this); }
    }
}
