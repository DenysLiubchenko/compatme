package ua.kpi.project.compatme.application.port.out;

import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;

/**
 * Outbound port for the preference-update audit log required by the thesis specification
 * ("preference update history/log"). Kept separate from {@link ProfileRepositoryPort} so the
 * persistence adapter can choose to store this as its own MongoDB collection without coupling
 * profile reads/writes to logging concerns.
 */
public interface PreferenceUpdateLogPort {

    void logUpdate(PreferenceUpdateLogEntry entry);

    record PreferenceUpdateLogEntry(
            ProfileId profileId,
            String previousPreferenceDescription,
            String updatedPreferenceDescription,
            String userMessage,
            String changeSummary,
            Instant occurredAt) {
    }
}
