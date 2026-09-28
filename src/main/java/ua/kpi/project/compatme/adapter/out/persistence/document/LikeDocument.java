package ua.kpi.project.compatme.adapter.out.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document for the {@code likes} collection. A compound unique index on
 * ({@code likerProfileId}, {@code likedProfileId}) prevents the same user from accumulating
 * duplicate like records for the same target; {@code likedProfileId} is separately indexed since
 * "Who Liked Me" queries filter by it.
 */
@Document(collection = "likes")
@CompoundIndex(name = "liker_liked_unique", def = "{'likerProfileId': 1, 'likedProfileId': 1}", unique = true)
public class LikeDocument {

    @Id
    private String id;

    private String likerProfileId;

    @Indexed
    private String likedProfileId;

    private Instant likedAt;

    public LikeDocument() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLikerProfileId() {
        return likerProfileId;
    }

    public void setLikerProfileId(String likerProfileId) {
        this.likerProfileId = likerProfileId;
    }

    public String getLikedProfileId() {
        return likedProfileId;
    }

    public void setLikedProfileId(String likedProfileId) {
        this.likedProfileId = likedProfileId;
    }

    public Instant getLikedAt() {
        return likedAt;
    }

    public void setLikedAt(Instant likedAt) {
        this.likedAt = likedAt;
    }
}
