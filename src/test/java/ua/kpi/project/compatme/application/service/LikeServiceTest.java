package ua.kpi.project.compatme.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.kpi.project.compatme.application.dto.RecordLikeResult;
import ua.kpi.project.compatme.application.port.out.LikeRepositoryPort;
import ua.kpi.project.compatme.application.port.out.ProfileRepositoryPort;
import ua.kpi.project.compatme.domain.model.ProfileId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link LikeService}'s mutual-match detection: liking someone who hasn't liked you
 * back is NOT a mutual match; liking someone who already liked you IS. A like is always recorded
 * regardless of mutuality — mutuality only changes what's reported back to the caller.
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeRepositoryPort likeRepository;

    @Mock
    private ProfileRepositoryPort profileRepository;

    @Test
    void recordLike_isNotMutual_whenNoReverseLikeExists() {
        LikeService service = new LikeService(likeRepository, profileRepository);
        ProfileId liker = ProfileId.generate();
        ProfileId liked = ProfileId.generate();
        when(likeRepository.existsByLikerAndLiked(liker, liked)).thenReturn(false);
        when(likeRepository.existsByLikerAndLiked(liked, liker)).thenReturn(false);

        RecordLikeResult result = service.recordLike(liker, liked);

        assertThat(result.mutualMatch()).isFalse();
        verify(likeRepository).save(any());
    }

    @Test
    void recordLike_isMutual_whenReverseLikeAlreadyExists() {
        LikeService service = new LikeService(likeRepository, profileRepository);
        ProfileId liker = ProfileId.generate();
        ProfileId liked = ProfileId.generate();
        when(likeRepository.existsByLikerAndLiked(liker, liked)).thenReturn(false);
        when(likeRepository.existsByLikerAndLiked(liked, liker)).thenReturn(true);

        RecordLikeResult result = service.recordLike(liker, liked);

        assertThat(result.mutualMatch()).isTrue();
        verify(likeRepository).save(any());
    }

    @Test
    void recordLike_doesNotSaveDuplicate_whenAlreadyLiked() {
        LikeService service = new LikeService(likeRepository, profileRepository);
        ProfileId liker = ProfileId.generate();
        ProfileId liked = ProfileId.generate();
        when(likeRepository.existsByLikerAndLiked(liker, liked)).thenReturn(true);
        when(likeRepository.existsByLikerAndLiked(liked, liker)).thenReturn(false);

        service.recordLike(liker, liked);

        verify(likeRepository, never()).save(any());
    }
}
