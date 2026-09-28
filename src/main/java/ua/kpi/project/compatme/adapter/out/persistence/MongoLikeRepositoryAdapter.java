package ua.kpi.project.compatme.adapter.out.persistence;

import org.springframework.stereotype.Component;
import ua.kpi.project.compatme.adapter.out.persistence.document.LikeDocument;
import ua.kpi.project.compatme.application.port.out.LikeRepositoryPort;
import ua.kpi.project.compatme.domain.model.Like;
import ua.kpi.project.compatme.domain.model.ProfileId;

import java.util.List;

/**
 * Outbound adapter implementing {@link LikeRepositoryPort} on top of Spring Data MongoDB, mirroring
 * {@link MongoProfileRepositoryAdapter}'s pattern.
 */
@Component
public class MongoLikeRepositoryAdapter implements LikeRepositoryPort {

    private final SpringDataLikeRepository springDataRepository;

    public MongoLikeRepositoryAdapter(SpringDataLikeRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Like save(Like like) {
        LikeDocument document = new LikeDocument();
        document.setLikerProfileId(like.likerProfileId().value());
        document.setLikedProfileId(like.likedProfileId().value());
        document.setLikedAt(like.likedAt());
        springDataRepository.save(document);
        return like;
    }

    @Override
    public boolean existsByLikerAndLiked(ProfileId liker, ProfileId liked) {
        return springDataRepository.existsByLikerProfileIdAndLikedProfileId(liker.value(), liked.value());
    }

    @Override
    public List<Like> findByLikedProfileId(ProfileId likedProfileId) {
        return springDataRepository.findByLikedProfileIdOrderByLikedAtDesc(likedProfileId.value()).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void deleteAllInvolvingProfile(ProfileId profileId) {
        springDataRepository.deleteByLikerProfileIdOrLikedProfileId(profileId.value(), profileId.value());
    }

    private Like toDomain(LikeDocument document) {
        return new Like(
                ProfileId.of(document.getLikerProfileId()),
                ProfileId.of(document.getLikedProfileId()),
                document.getLikedAt());
    }
}
