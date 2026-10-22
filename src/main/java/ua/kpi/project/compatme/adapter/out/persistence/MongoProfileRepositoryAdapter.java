package ua.kpi.project.compatme.adapter.out.persistence;

import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.adapter.out.persistence.document.ProfileDocument;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.util.List;
import java.util.Optional;

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

    public MongoProfileRepositoryAdapter(SpringDataProfileRepository springDataRepository, ProfilePersistenceMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
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
            documents = springDataRepository.findByIdNotAndAgeBetween(excludingId.value(), filter.minAge(), filter.maxAge());
        } else {
            documents = springDataRepository.findByIdNot(excludingId.value());
        }
        return documents.stream().map(mapper::toDomain).toList();
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
