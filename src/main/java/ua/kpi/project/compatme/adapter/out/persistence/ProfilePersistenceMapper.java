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

/** Persistence boundary between the framework-free Profile aggregate and MongoDB documents. */
@Component
public class ProfilePersistenceMapper {

    public ProfileDocument toDocument(Profile profile) {
        ProfileDocument document = new ProfileDocument();
        document.setId(profile.id().value());
        document.setTelegramUserId(profile.telegramUserId());
        document.setDisplayName(profile.displayName());
        document.setAge(profile.age());
        document.setGender(profile.gender().name());
        document.setOrientation(profile.orientation());
        document.setCountry(profile.country());
        document.setCity(profile.city());
        document.setSeekingGenders(profile.seekingGenders().stream().map(Enum::name).collect(Collectors.toSet()));
        document.setSelfDescription(profile.selfDescription());
        document.setPreferenceDescription(profile.preferenceDescription());
        document.setSelfEmbedding(toDocument(profile.embeddings().selfEmbedding()));
        document.setPreferenceEmbedding(toDocument(profile.embeddings().preferenceEmbedding()));
        document.setCreatedAt(profile.createdAt());
        document.setUpdatedAt(profile.updatedAt());
        document.setArchetypeIds(profile.archetypeIds());
        document.setPhotoUrls(profile.photoUrls());
        document.setStatus(profile.status());
        document.setBodyType(profile.bodyType());
        document.setDiet(profile.diet());
        document.setDrinks(profile.drinks());
        document.setDrugs(profile.drugs());
        document.setEducation(profile.education());
        document.setEthnicity(profile.ethnicity());
        document.setHeight(profile.height());
        document.setIncome(profile.income());
        document.setJob(profile.job());
        document.setLastOnline(profile.lastOnline());
        document.setOffspring(profile.offspring());
        document.setPets(profile.pets());
        document.setReligion(profile.religion());
        document.setSign(profile.sign());
        document.setSmokes(profile.smokes());
        document.setSpeaks(profile.speaks());
        document.setPhotoUrns(profile.photoUrns());
        return document;
    }

    public Profile toDomain(ProfileDocument document) {
        Set<Gender> seekingGenders = document.getSeekingGenders() == null
                ? Set.of()
                : document.getSeekingGenders().stream().map(Gender::valueOf).collect(Collectors.toSet());
        ProfileEmbeddings embeddings = new ProfileEmbeddings(
                toDomain(document.getSelfEmbedding()), toDomain(document.getPreferenceEmbedding()));
        return Profile.builder()
                .id(ProfileId.of(document.getId()))
                .telegramUserId(document.getTelegramUserId())
                .displayName(document.getDisplayName())
                .age(document.getAge())
                .gender(Gender.valueOf(document.getGender()))
                .orientation(document.getOrientation())
                .country(document.getCountry())
                .city(document.getCity())
                .seekingGenders(seekingGenders)
                .selfDescription(document.getSelfDescription())
                .preferenceDescription(document.getPreferenceDescription())
                .embeddings(embeddings)
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .archetypeIds(document.getArchetypeIds())
                .photoUrls(document.getPhotoUrls())
                .status(document.getStatus())
                .bodyType(document.getBodyType())
                .diet(document.getDiet())
                .drinks(document.getDrinks())
                .drugs(document.getDrugs())
                .education(document.getEducation())
                .ethnicity(document.getEthnicity())
                .height(document.getHeight())
                .income(document.getIncome())
                .job(document.getJob())
                .lastOnline(document.getLastOnline())
                .offspring(document.getOffspring())
                .pets(document.getPets())
                .religion(document.getReligion())
                .sign(document.getSign())
                .smokes(document.getSmokes())
                .speaks(document.getSpeaks())
                .photoUrns(document.getPhotoUrns())
                .build();
    }

    private EmbeddingVectorDocument toDocument(EmbeddingVector vector) {
        if (vector == null) return null;
        return new EmbeddingVectorDocument(vector.values(), vector.modelName(), vector.dimensionality(),
                vector.sourceTextHash(), vector.computedAt());
    }

    private EmbeddingVector toDomain(EmbeddingVectorDocument document) {
        if (document == null) return null;
        return new EmbeddingVector(document.getValues(), document.getModelName(), document.getDimensionality(),
                document.getSourceTextHash(), document.getComputedAt());
    }
}
