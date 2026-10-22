package ua.kpi.project.compatme.adapter.out.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import ua.kpi.project.compatme.adapter.out.persistence.document.ProfileDocument;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository — pure infrastructure plumbing, used only internally by
 * {@link MongoProfileRepositoryAdapter}. Never referenced outside {@code adapter.out.persistence}.
 */
public interface SpringDataProfileRepository extends MongoRepository<ProfileDocument, String> {

    Optional<ProfileDocument> findByTelegramUserId(String telegramUserId);

    @Query("{ '_id': { '$ne': ?0 }, 'age': { '$gte': ?1, '$lte': ?2 } }")
    List<ProfileDocument> findByIdNotAndAgeBetweenInclusive(String excludedId, int minAge, int maxAge);

    List<ProfileDocument> findByIdNot(String excludedId);
}
