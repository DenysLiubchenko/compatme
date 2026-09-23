package ua.kpi.project.compatme.adapter.out.persistence;

import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.adapter.out.persistence.document.EmbeddingVectorDocument;
import ua.kpi.project.compatme.adapter.out.persistence.document.ProfileDocument;
import ua.kpi.project.compatme.domain.model.EmbeddingVector;
import ua.kpi.project.compatme.domain.model.Gender;
import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileEmbeddings;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps between the framework-agnostic domain {@link Profile} aggregate and the MongoDB-specific
 * {@link ProfileDocument}. This class is the single translation boundary between the two
 * representations — the domain layer never sees {@link ProfileDocument}, and this adapter never
 * exposes {@link ProfileDocument} outside {@code adapter.out.persistence}.
 */
@Component
public class ProfilePersistenceMapper {

    public ProfileDocument toDocument(Profile profile) {
        ProfileDocument document = new ProfileDocument();
        document.setId(profile.id().value());
        document.setTelegramUserId(profile.telegramUserId());
        document.setDisplayName(profile.displayName());
        document.setAge(profile.age());
        document.setGender(profile.gender() == null ? null : profile.gender().name());
        document.setSeekingGenders(profile.seekingGenders().stream().map(Enum::name).collect(Collectors.toSet()));
        document.setSelfDescription(profile.selfDescription());
        document.setPreferenceDescription(profile.preferenceDescription());
        document.setSelfEmbedding(toDocument(profile.embeddings().selfEmbedding()));
        document.setPreferenceEmbedding(toDocument(profile.embeddings().preferenceEmbedding()));
        document.setCreatedAt(profile.createdAt());
        document.setUpdatedAt(profile.updatedAt());
        document.setArchetypeIds(profile.archetypeIds());
        return document;
    }

    public Profile toDomain(ProfileDocument document) {
        Set<Gender> seekingGenders = document.getSeekingGenders() == null
                ? Set.of()
                : document.getSeekingGenders().stream().map(Gender::valueOf).collect(Collectors.toSet());
        ProfileEmbeddings embeddings = new ProfileEmbeddings(
                toDomain(document.getSelfEmbedding()), toDomain(document.getPreferenceEmbedding()));
        return new Profile(
                ProfileId.of(document.getId()),
                document.getTelegramUserId(),
                document.getDisplayName(),
                document.getAge(),
                document.getGender() == null ? null : Gender.valueOf(document.getGender()),
                seekingGenders,
                document.getSelfDescription(),
                document.getPreferenceDescription(),
                embeddings,
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getArchetypeIds());
    }

    private EmbeddingVectorDocument toDocument(EmbeddingVector vector) {
        if (vector == null) {
            return null;
        }
        return new EmbeddingVectorDocument(
                vector.values(), vector.modelName(), vector.dimensionality(), vector.sourceTextHash(), vector.computedAt());
    }

    private EmbeddingVector toDomain(EmbeddingVectorDocument document) {
        if (document == null) {
            return null;
        }
        return new EmbeddingVector(
                document.getValues(), document.getModelName(), document.getDimensionality(),
                document.getSourceTextHash(), document.getComputedAt());
    }
}
