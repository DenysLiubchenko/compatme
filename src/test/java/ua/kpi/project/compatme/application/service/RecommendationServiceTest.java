package ua.kpi.project.compatme.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.kpi.project.compatme.application.dto.GetRecommendationsQuery;
import ua.kpi.project.compatme.application.dto.RecommendationResult;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.EmbeddingVector;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.LocationScope;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileEmbeddings;
import ua.kpi.project.compatme.domain.model.ProfileId;
import ua.kpi.project.compatme.domain.service.CompatibilityScorer;
import ua.kpi.project.compatme.domain.service.ReciprocalHarmonicAggregationStrategy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private ProfileRepositoryPort profileRepository;

    private final CompatibilityScorer scorer = new CompatibilityScorer(new ReciprocalHarmonicAggregationStrategy());

    @Test
    void recommend_excludesCandidatesWithoutEmbeddingsAndSortsDescendingByScore() {
        // GIVEN
        RecommendationService service = new RecommendationService(profileRepository, scorer);
        Profile requester = profileWith("requester", 28, embedding(new float[]{1f, 0f}), embedding(new float[]{0f, 1f}));
        Profile strongMatch = profileWith("strong", 30, embedding(new float[]{0f, 1f}), embedding(new float[]{1f, 0f}));
        Profile weakMatch = profileWith("weak", 26, embedding(new float[]{1f, 1f}), embedding(new float[]{1f, 1f}));
        Profile noEmbeddings = profileWithoutEmbeddings("no-embeddings", 27);

        when(profileRepository.findById(requester.id())).thenReturn(Optional.of(requester));
        when(profileRepository.findCandidates(any(), any()))
                .thenReturn(List.of(weakMatch, strongMatch, noEmbeddings));

        // WHEN
        List<RecommendationResult> results = service.recommend(
                new GetRecommendationsQuery(requester.id().value(), 10));

        // THEN
        assertThat(results).hasSize(2);
        assertThat(results.get(0).candidateProfile().id()).isEqualTo(strongMatch.id());
        assertThat(results.get(0).match().aggregatedScore())
                .isGreaterThan(results.get(1).match().aggregatedScore());
    }

    @Test
    void recommend_appliesTopNLimit() {
        // GIVEN
        RecommendationService service = new RecommendationService(profileRepository, scorer);
        Profile requester = profileWith("requester", 28, embedding(new float[]{1f, 0f}), embedding(new float[]{0f, 1f}));
        List<Profile> manyCandidates = List.of(
                profileWith("a", 28, embedding(new float[]{0f, 1f}), embedding(new float[]{1f, 0f})),
                profileWith("b", 28, embedding(new float[]{0f, 1f}), embedding(new float[]{1f, 0f})),
                profileWith("c", 28, embedding(new float[]{0f, 1f}), embedding(new float[]{1f, 0f})));

        when(profileRepository.findById(requester.id())).thenReturn(Optional.of(requester));
        when(profileRepository.findCandidates(any(), any())).thenReturn(manyCandidates);

        // WHEN
        List<RecommendationResult> results = service.recommend(
                new GetRecommendationsQuery(requester.id().value(), 2));

        // THEN
        assertThat(results).hasSize(2);
    }

    @Test
    void recommend_scopeIsThreadedThroughAndChangesCandidatePool() {
        // GIVEN requester in Kyiv, Ukraine and three otherwise identical candidates
        RecommendationService service = new RecommendationService(profileRepository, scorer);
        Profile requester = located("requester", "Kyiv", "Ukraine");
        Profile sameCity = located("same-city", "Kyiv", "Ukraine");
        Profile sameCountry = located("same-country", "Lviv", "Ukraine");
        Profile abroad = located("abroad", "Berlin", "Germany");
        when(profileRepository.findById(requester.id())).thenReturn(Optional.of(requester));
        when(profileRepository.findCandidates(any(), any())).thenReturn(List.of(sameCity, sameCountry, abroad));

        // WHEN / THEN
        assertThat(ids(service.recommend(new GetRecommendationsQuery(requester.id().value(), 10, LocationScope.CITY))))
                .containsExactly(sameCity.id());
        assertThat(ids(service.recommend(new GetRecommendationsQuery(requester.id().value(), 10, LocationScope.COUNTRY))))
                .containsExactlyInAnyOrder(sameCity.id(), sameCountry.id());
        assertThat(ids(service.recommend(new GetRecommendationsQuery(requester.id().value(), 10, LocationScope.WORLDWIDE))))
                .containsExactlyInAnyOrder(sameCity.id(), sameCountry.id(), abroad.id());
    }

    @Test
    void recommend_withoutExplicitScope_usesRequesterDefaultSearchScope() {
        RecommendationService service = new RecommendationService(profileRepository, scorer);
        Profile requester = located("requester", "Kyiv", "Ukraine", LocationScope.COUNTRY);
        Profile sameCountry = located("same-country", "Lviv", "Ukraine");
        Profile abroad = located("abroad", "Berlin", "Germany");
        when(profileRepository.findById(requester.id())).thenReturn(Optional.of(requester));
        when(profileRepository.findCandidates(any(), any())).thenReturn(List.of(sameCountry, abroad));

        assertThat(ids(service.recommend(new GetRecommendationsQuery(requester.id().value(), 10))))
                .containsExactly(sameCountry.id());
    }

    private static List<ProfileId> ids(List<RecommendationResult> results) {
        return results.stream().map(r -> r.candidateProfile().id()).toList();
    }

    private static Profile located(String name, String city, String country) {
        return located(name, city, country, null);
    }

    private static Profile located(String name, String city, String country, LocationScope defaultScope) {
        Instant now = Instant.now();
        boolean requester = name.equals("requester");
        return Profile.builder().id(ProfileId.generate()).displayName(name).age(28)
                .gender(requester ? Gender.MALE : Gender.FEMALE)
                .orientation(ua.kpi.project.compatme.domain.model.Orientation.STRAIGHT)
                .location(new ua.kpi.project.compatme.domain.model.Location(city, country))
                .searchScope(defaultScope)
                .seekingGenders(requester ? Set.of(Gender.FEMALE) : Set.of(Gender.MALE))
                .selfDescription("self description").preferenceDescription("preference description")
                .embeddings(new ProfileEmbeddings(embedding(new float[]{1f, 0f}), embedding(new float[]{0f, 1f})))
                .createdAt(now).updatedAt(now).build();
    }

    private static Profile profileWith(String name, int age, EmbeddingVector selfEmbedding, EmbeddingVector prefEmbedding) {
        Instant now = Instant.now();
        boolean requester = name.equals("requester");
        Gender gender = requester ? Gender.MALE : Gender.FEMALE;
        Set<Gender> seeking = requester ? Set.of(Gender.FEMALE) : Set.of(Gender.MALE);
        return Profile.builder().id(ProfileId.generate()).displayName(name).age(age).gender(gender)
                .orientation(ua.kpi.project.compatme.domain.model.Orientation.STRAIGHT)
                .country("United States").city("New York").seekingGenders(seeking)
                .selfDescription("self description").preferenceDescription("preference description")
                .embeddings(new ProfileEmbeddings(selfEmbedding, prefEmbedding)).createdAt(now).updatedAt(now).build();
    }

    private static Profile profileWithoutEmbeddings(String name, int age) {
        Instant now = Instant.now();
        boolean requester = name.equals("requester");
        Gender gender = requester ? Gender.MALE : Gender.FEMALE;
        Set<Gender> seeking = requester ? Set.of(Gender.FEMALE) : Set.of(Gender.MALE);
        return Profile.builder().id(ProfileId.generate()).displayName(name).age(age).gender(gender)
                .orientation(ua.kpi.project.compatme.domain.model.Orientation.STRAIGHT)
                .country("United States").city("New York").seekingGenders(seeking)
                .selfDescription("self description").preferenceDescription("preference description")
                .embeddings(ProfileEmbeddings.empty()).createdAt(now).updatedAt(now).build();
    }

    private static EmbeddingVector embedding(float[] values) {
        return new EmbeddingVector(values, "gemini-embedding-001", values.length, "hash", Instant.now());
    }
}
