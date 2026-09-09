package ua.kpi.project.compatme.adapter.out.persistence;

import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.adapter.out.persistence.document.PreferenceUpdateLogDocument;
import ua.kpi.project.compatme.application.port.out.PreferenceUpdateLogPort;

/**
 * Outbound adapter implementing {@link PreferenceUpdateLogPort} on top of Spring Data MongoDB.
 */
@Component
public class MongoPreferenceUpdateLogAdapter implements PreferenceUpdateLogPort {

    private final SpringDataPreferenceUpdateLogRepository repository;

    public MongoPreferenceUpdateLogAdapter(SpringDataPreferenceUpdateLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void logUpdate(PreferenceUpdateLogEntry entry) {
        PreferenceUpdateLogDocument document = new PreferenceUpdateLogDocument();
        document.setProfileId(entry.profileId().value());
        document.setPreviousPreferenceDescription(entry.previousPreferenceDescription());
        document.setUpdatedPreferenceDescription(entry.updatedPreferenceDescription());
        document.setUserMessage(entry.userMessage());
        document.setChangeSummary(entry.changeSummary());
        document.setOccurredAt(entry.occurredAt());
        repository.save(document);
    }
}
