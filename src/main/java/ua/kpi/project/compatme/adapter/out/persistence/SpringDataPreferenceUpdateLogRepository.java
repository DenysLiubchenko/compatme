package ua.kpi.project.compatme.adapter.out.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;
import ua.kpi.project.compatme.adapter.out.persistence.document.PreferenceUpdateLogDocument;

/**
 * Spring Data MongoDB repository for the preference-update audit log — pure infrastructure
 * plumbing, used only internally by {@link MongoPreferenceUpdateLogAdapter}.
 */
public interface SpringDataPreferenceUpdateLogRepository extends MongoRepository<PreferenceUpdateLogDocument, String> {
}
