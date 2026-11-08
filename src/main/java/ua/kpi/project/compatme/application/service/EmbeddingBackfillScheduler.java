package ua.kpi.project.compatme.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.application.port.in.GenerateEmbeddingsUseCase;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.Profile;

import java.util.regex.Pattern;

/**
 * Self-heals after an embedding-provider outage: periodically completes missing or stale
 * embeddings of real users (profiles with a Telegram account; bulk sample data is never touched).
 * Up-to-date profiles cost no provider calls, so this is cheap while the provider is healthy.
 */
@Component
public class EmbeddingBackfillScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfillScheduler.class);
    private static final Pattern NUMERIC = Pattern.compile("[0-9]+");

    private final ProfileRepositoryPort profileRepository;
    private final GenerateEmbeddingsUseCase generateEmbeddings;

    public EmbeddingBackfillScheduler(ProfileRepositoryPort profileRepository, GenerateEmbeddingsUseCase generateEmbeddings) {
        this.profileRepository = profileRepository;
        this.generateEmbeddings = generateEmbeddings;
    }

    @Scheduled(
            fixedDelayString = "${app.embedding-backfill.interval-millis:120000}",
            initialDelayString = "${app.embedding-backfill.interval-millis:120000}")
    public void completePendingEmbeddings() {
        for (Profile profile : profileRepository.findAll()) {
            if (profile.telegramUserId() == null || !NUMERIC.matcher(profile.telegramUserId()).matches()) {
                continue;
            }
            try {
                var result = generateEmbeddings.generateEmbeddings(profile.id());
                if (result.selfEmbeddingRecomputed() || result.preferenceEmbeddingRecomputed()) {
                    log.info("Backfilled embeddings for profile {}", profile.id());
                }
                if (result.pending()) {
                    return; // provider still unavailable: stop early, retry next cycle
                }
            } catch (RuntimeException e) {
                log.warn("Embedding backfill failed for profile {}: {}", profile.id(), e.getMessage());
            }
        }
    }
}
