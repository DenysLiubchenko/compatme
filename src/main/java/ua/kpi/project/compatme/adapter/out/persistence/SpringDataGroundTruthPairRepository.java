package ua.kpi.project.compatme.adapter.out.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;
import ua.kpi.project.compatme.adapter.out.persistence.document.GroundTruthPairDocument;

/**
 * Spring Data MongoDB repository for the ground-truth evaluation dataset — pure infrastructure
 * plumbing, used only internally by {@link MongoGroundTruthPairRepositoryAdapter}.
 */
public interface SpringDataGroundTruthPairRepository extends MongoRepository<GroundTruthPairDocument, String> {
}
