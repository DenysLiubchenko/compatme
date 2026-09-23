package ua.kpi.project.compatme.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.application.port.out.GroundTruthPairRepositoryPort;
import ua.kpi.project.compatme.domain.model.GroundTruthLabel;
import ua.kpi.project.compatme.domain.model.GroundTruthPair;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.io.InputStream;
import java.util.List;

/**
 * Imports the thesis evaluation's ground-truth dataset (pairs of profile ids with an expected
 * reciprocal-compatibility label) from {@code classpath:ground-truth.json} into MongoDB.
 *
 * <p>Gated behind {@code app.ground-truth.enabled=true} (bound to the {@code
 * GROUND_TRUTH_IMPORT_ENABLED} environment variable), mirroring {@link ProfileDataLoader}'s
 * {@code app.seed-data.enabled} pattern, so this never runs unintentionally. Each run fully
 * replaces the previously imported dataset — the ground-truth collection is treated as a
 * write-once import target, not something individual pairs are appended to over time.
 */
@Component
@ConditionalOnProperty(prefix = "app.ground-truth", name = "enabled", havingValue = "true")
public class GroundTruthDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(GroundTruthDataLoader.class);

    private final GroundTruthPairRepositoryPort groundTruthPairRepository;
    private final ObjectMapper objectMapper;
    private final Resource groundTruthResource;

    public GroundTruthDataLoader(
            GroundTruthPairRepositoryPort groundTruthPairRepository,
            ObjectMapper objectMapper,
            ResourceLoader resourceLoader) {
        this.groundTruthPairRepository = groundTruthPairRepository;
        this.objectMapper = objectMapper;
        this.groundTruthResource = resourceLoader.getResource("classpath:ground-truth.json");
    }

    @Override
    public void run(String... args) throws Exception {
        if (!groundTruthResource.exists()) {
            log.warn("ground-truth.json not found on classpath; skipping ground-truth import");
            return;
        }

        List<GroundTruthPairEntry> entries;
        try (InputStream inputStream = groundTruthResource.getInputStream()) {
            entries = objectMapper.readValue(inputStream, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, GroundTruthPairEntry.class));
        }

        List<GroundTruthPair> pairs = entries.stream().map(GroundTruthPairEntry::toDomain).toList();
        log.info("Importing {} ground-truth pairs (replacing any previously imported dataset)...", pairs.size());
        groundTruthPairRepository.replaceAll(pairs);
        log.info("Ground-truth import complete.");
    }

    /** Deserialization target for entries in {@code ground-truth.json}. */
    private record GroundTruthPairEntry(String profileAId, String profileBId, String label) {

        GroundTruthPair toDomain() {
            return new GroundTruthPair(ProfileId.of(profileAId), ProfileId.of(profileBId), GroundTruthLabel.valueOf(label));
        }
    }
}
