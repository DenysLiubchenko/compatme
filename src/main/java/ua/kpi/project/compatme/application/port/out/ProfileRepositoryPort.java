package ua.kpi.project.compatme.application.port.out;

import ua.kpi.project.compatme.domain.model.Profile;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for profile persistence. Implemented by {@code adapter.out.persistence} (MongoDB
 * today). The application/domain layers depend only on this interface, never on Spring Data or
 * MongoDB types directly — swapping the database only requires a new adapter implementing this
 * port.
 */
public interface ProfileRepositoryPort {

    Profile save(Profile profile);

    Optional<Profile> findById(ProfileId id);

    Optional<Profile> findByTelegramUserId(String telegramUserId);

    /**
     * Returns candidate profiles for recommendation, already narrowed by the cheap hard filters
     * (age range, reciprocal gender/seeking-gender match) that don't require embeddings. Excludes
     * the requester itself.
     */
    List<Profile> findCandidates(ProfileId excludingId, CandidateFilter filter);

    List<Profile> findAll();

    void deleteById(ProfileId id);

    boolean existsById(ProfileId id);

    /**
     * Hard pre-filter criteria applied before NLP-based compatibility scoring, to avoid scoring
     * candidates that could never be a viable match regardless of description similarity.
     *
     * @param minAge inclusive lower age bound, or {@code null} for no lower bound
     * @param maxAge inclusive upper age bound, or {@code null} for no upper bound
     */
    record CandidateFilter(Integer minAge, Integer maxAge) {

        public static CandidateFilter none() {
            return new CandidateFilter(null, null);
        }
    }
}
