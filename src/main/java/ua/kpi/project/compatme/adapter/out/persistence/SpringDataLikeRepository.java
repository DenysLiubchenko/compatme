package ua.kpi.project.compatme.adapter.out.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;
import ua.kpi.project.compatme.adapter.out.persistence.document.LikeDocument;

import java.util.List;

/**
 * Spring Data MongoDB repository for likes — pure infrastructure plumbing, used only internally
 * by {@link MongoLikeRepositoryAdapter}.
 */
public interface SpringDataLikeRepository extends MongoRepository<LikeDocument, String> {

    boolean existsByLikerProfileIdAndLikedProfileId(String likerProfileId, String likedProfileId);

    List<LikeDocument> findByLikedProfileIdOrderByLikedAtDesc(String likedProfileId);

    void deleteByLikerProfileIdOrLikedProfileId(String likerProfileId, String likedProfileId);
}
