package ua.kpi.project.compatme.adapter.out.persistence;

import org.springframework.stereotype.Component;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import ua.kpi.project.compatme.adapter.out.persistence.document.ProfileDocument;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;
import ua.kpi.project.compatme.domain.model.LocationScope;

import java.util.List;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.ArrayList;

/**
 * Outbound adapter implementing {@link ProfileRepositoryPort} on top of Spring Data MongoDB.
 * This is the only class (besides {@link ProfilePersistenceMapper} and the documents themselves)
 * that knows MongoDB exists; swapping to a different database means writing a new class
 * implementing {@link ProfileRepositoryPort} and rewiring the bean in {@code config} — nothing
 * in {@code domain} or {@code application} would need to change.
 */
@Component
public class MongoProfileRepositoryAdapter implements ProfileRepositoryPort {

    private final SpringDataProfileRepository springDataRepository;
    private final ProfilePersistenceMapper mapper;
    private final MongoTemplate mongoTemplate;
    private final AtlasVectorSearchProperties vectorSearchProperties;

    public MongoProfileRepositoryAdapter(
            SpringDataProfileRepository springDataRepository,
            ProfilePersistenceMapper mapper,
            MongoTemplate mongoTemplate,
            AtlasVectorSearchProperties vectorSearchProperties) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
        this.mongoTemplate = mongoTemplate;
        this.vectorSearchProperties = vectorSearchProperties;
    }

    @Override
    public Profile save(Profile profile) {
        ProfileDocument saved = springDataRepository.save(mapper.toDocument(profile));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Profile> findById(ProfileId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Optional<Profile> findByTelegramUserId(String telegramUserId) {
        return springDataRepository.findByTelegramUserId(telegramUserId).map(mapper::toDomain);
    }

    @Override
    public List<Profile> findCandidates(ProfileId excludingId, CandidateFilter filter) {
        List<ProfileDocument> documents;
        if (filter.minAge() != null && filter.maxAge() != null) {
            documents = springDataRepository.findByIdNotAndAgeBetweenInclusive(
                    excludingId.value(), filter.minAge(), filter.maxAge());
        } else {
            documents = springDataRepository.findByIdNot(excludingId.value());
        }
        return documents.stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Profile> findVectorCandidates(Profile requester, CandidateFilter filter, LocationScope scope, int topN) {
        if (!vectorSearchProperties.isEnabled()) {
            return findCandidates(requester.id(), filter);
        }
        int limit = Math.multiplyExact(topN, vectorSearchProperties.getCandidatesPerResult());
        int numCandidates = Math.multiplyExact(limit, 10);
        LinkedHashSet<String> candidateIds = new LinkedHashSet<>();
        candidateIds.addAll(vectorSearch(
                vectorSearchProperties.getSelfIndex(), "selfEmbedding.values",
                requester.embeddings().preferenceEmbedding().values(), requester, filter, scope, limit, numCandidates));
        candidateIds.addAll(vectorSearch(
                vectorSearchProperties.getPreferenceIndex(), "preferenceEmbedding.values",
                requester.embeddings().selfEmbedding().values(), requester, filter, scope, limit, numCandidates));
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        return springDataRepository.findAllById(candidateIds).stream().map(mapper::toDomain).toList();
    }

    private List<String> vectorSearch(
            String index, String path, float[] queryVector, Profile requester, CandidateFilter filter, LocationScope scope, int limit,
            int numCandidates) {
        List<Double> vector = new ArrayList<>(queryVector.length);
        for (float value : queryVector) {
            vector.add((double) value);
        }
        Document vectorSearchOptions = new Document("index", index)
                .append("path", path)
                .append("queryVector", vector)
                .append("numCandidates", numCandidates)
                .append("limit", limit);
        List<Document> filters = new ArrayList<>();
        if (filter.minAge() != null && filter.maxAge() != null) {
            filters.add(new Document("age", new Document("$gte", filter.minAge()).append("$lte", filter.maxAge())));
        }
        filters.add(new Document("gender", new Document("$in", requester.seekingGenders().stream().map(Enum::name).toList())));
        filters.add(new Document("seekingGenders", requester.gender().name()));
        if (requester.age() != null) {
            filters.add(new Document("minPreferredAge", new Document("$lte", requester.age())));
            filters.add(new Document("maxPreferredAge", new Document("$gte", requester.age())));
        }
        if (scope != LocationScope.WORLDWIDE) {
            filters.add(new Document("country", requester.location().country()));
        }
        if (scope == LocationScope.CITY) {
            filters.add(new Document("city", requester.location().city()));
        }
        filters.add(candidateAcceptsRequesterLocation(requester));
        vectorSearchOptions.append("filter", new Document("$and", filters));
        Document vectorSearch = new Document("$vectorSearch", vectorSearchOptions);
        List<Document> documents = mongoTemplate.getCollection("profiles")
                .aggregate(List.of(vectorSearch, new Document("$project", new Document("_id", 1))))
                .into(new ArrayList<>());
        return documents.stream().map(document -> document.getString("_id")).toList();
    }

    private Document candidateAcceptsRequesterLocation(Profile requester) {
        Document sameCountry = new Document("searchScope", LocationScope.COUNTRY.name())
                .append("country", requester.location().country());
        Document sameCity = new Document("searchScope", LocationScope.CITY.name())
                .append("country", requester.location().country())
                .append("city", requester.location().city());
        return new Document("$or", List.of(
                new Document("searchScope", LocationScope.WORLDWIDE.name()), sameCountry, sameCity));
    }

    @Override
    public List<Profile> findAll() {
        return springDataRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(ProfileId id) {
        springDataRepository.deleteById(id.value());
    }

    @Override
    public boolean existsById(ProfileId id) {
        return springDataRepository.existsById(id.value());
    }
}
