package ua.kpi.project.compatme.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ua.kpi.project.compatme.application.port.out.ProfileAttributeExtractionPort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.time.Instant;

/** Enriches persisted profiles without delaying the account-creation response. */
@Service
public class ProfileAttributeEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(ProfileAttributeEnrichmentService.class);

    private final ProfileRepositoryPort profileRepository;
    private final ProfileAttributeExtractionPort attributeExtraction;

    public ProfileAttributeEnrichmentService(
            ProfileRepositoryPort profileRepository, ProfileAttributeExtractionPort attributeExtraction) {
        this.profileRepository = profileRepository;
        this.attributeExtraction = attributeExtraction;
    }

    @Async("profileAttributeExecutor")
    public void enrich(ProfileId profileId, String selfDescription, String preferenceDescription) {
        try {
            var fields = attributeExtraction.extract(selfDescription, preferenceDescription);
            profileRepository.findById(profileId).ifPresent(profile -> saveIfCurrent(
                    profile, selfDescription, preferenceDescription, fields));
        } catch (RuntimeException e) {
            log.warn("Optional profile-field enrichment failed for profile {}: {}", profileId.value(), e.getMessage());
        }
    }

    private void saveIfCurrent(Profile profile, String selfDescription, String preferenceDescription,
                               ua.kpi.project.compatme.domain.model.OptionalProfileFields fields) {
        if (!profile.selfDescription().equals(selfDescription)
                || !profile.preferenceDescription().equals(preferenceDescription)) {
            return;
        }
        profileRepository.save(profile.withOptionalFields(fields, Instant.now()));
    }
}
