package ua.kpi.project.compatme.adapter.out.persistence;

import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.adapter.out.persistence.document.GroundTruthPairDocument;
import ua.kpi.project.compatme.application.port.out.GroundTruthPairRepositoryPort;
import ua.kpi.project.compatme.domain.model.GroundTruthLabel;
import ua.kpi.project.compatme.domain.model.GroundTruthPair;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;
import java.util.List;

/**
 * Outbound adapter implementing {@link GroundTruthPairRepositoryPort} on top of Spring Data
 * MongoDB. Mirrors {@link MongoProfileRepositoryAdapter}'s pattern: this class and the document
 * type are the only place MongoDB is visible for ground-truth data.
 */
@Component
public class MongoGroundTruthPairRepositoryAdapter implements GroundTruthPairRepositoryPort {

    private final SpringDataGroundTruthPairRepository springDataRepository;

    public MongoGroundTruthPairRepositoryAdapter(SpringDataGroundTruthPairRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public void replaceAll(List<GroundTruthPair> pairs) {
        springDataRepository.deleteAll();
        Instant importedAt = Instant.now();
        List<GroundTruthPairDocument> documents = pairs.stream().map(pair -> toDocument(pair, importedAt)).toList();
        springDataRepository.saveAll(documents);
    }

    @Override
    public List<GroundTruthPair> findAll() {
        return springDataRepository.findAll().stream().map(this::toDomain).toList();
    }

    private GroundTruthPairDocument toDocument(GroundTruthPair pair, Instant importedAt) {
        GroundTruthPairDocument document = new GroundTruthPairDocument();
        document.setProfileAId(pair.profileAId().value());
        document.setProfileBId(pair.profileBId().value());
        document.setExpectedLabel(pair.expectedLabel().name());
        document.setImportedAt(importedAt);
        return document;
    }

    private GroundTruthPair toDomain(GroundTruthPairDocument document) {
        return new GroundTruthPair(
                ProfileId.of(document.getProfileAId()),
                ProfileId.of(document.getProfileBId()),
                GroundTruthLabel.valueOf(document.getExpectedLabel()));
    }
}
