package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.domain.model.media.MediaOrigin;
import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, Long> {

    List<MediaAsset> findByMediaType(MediaType mediaType);

    List<MediaAsset> findByChecksum(String checksum);

    List<MediaAsset> findBySourceReferenceId(Long sourceReferenceId);

    boolean existsBySourceReference_Id(Long sourceReferenceId);

    @EntityGraph(attributePaths = "sourceReference")
    @Query("""
            SELECT asset
            FROM MediaAsset asset
            WHERE asset.origin <> :generatedOrigin
               OR EXISTS (
                    SELECT figure.id
                    FROM DocumentPageFigure figure
                    WHERE figure.mediaAsset = asset
                      AND figure.reviewState = :approvedState
               )
            """)
    Page<MediaAsset> findVisibleInGeneralLibrary(
            @Param("generatedOrigin") MediaOrigin generatedOrigin,
            @Param("approvedState") FigureReviewState approvedState,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "sourceReference")
    @Query("""
            SELECT asset
            FROM MediaAsset asset
            WHERE asset.id = :assetId
              AND (
                    asset.origin <> :generatedOrigin
                    OR EXISTS (
                        SELECT figure.id
                        FROM DocumentPageFigure figure
                        WHERE figure.mediaAsset = asset
                          AND figure.reviewState = :approvedState
                    )
              )
            """)
    Optional<MediaAsset> findVisibleInGeneralLibraryById(
            @Param("assetId") Long assetId,
            @Param("generatedOrigin") MediaOrigin generatedOrigin,
            @Param("approvedState") FigureReviewState approvedState
    );

    @Query("""
            SELECT asset
            FROM MediaAsset asset
            WHERE asset.id = :assetId
              AND (
                    NOT EXISTS (
                        SELECT figure.id
                        FROM DocumentPageFigure figure
                        WHERE figure.mediaAsset = asset
                    )
                    OR EXISTS (
                        SELECT figure.id
                        FROM DocumentPageFigure figure
                        WHERE figure.mediaAsset = asset
                          AND figure.reviewState = :approvedState
                    )
              )
            """)
    Optional<MediaAsset> findPubliclyDeliverableById(
            @Param("assetId") Long assetId,
            @Param("approvedState") FigureReviewState approvedState
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT asset FROM MediaAsset asset WHERE asset.id = :assetId")
    java.util.Optional<MediaAsset> findByIdForUpdate(
            @Param("assetId") Long assetId
    );
}
