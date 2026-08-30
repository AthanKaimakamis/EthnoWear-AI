package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import fmi.ethnowear.domain.model.document.figure.FigureReviewState;

import java.util.Collection;
import java.util.List;

public interface ArchiveItemMediaRepository extends JpaRepository<ArchiveItemMedia, Long> {

    @EntityGraph(attributePaths = {"mediaAsset", "mediaAsset.sourceReference"})
    List<ArchiveItemMedia> findByArchiveItemId(Long archiveItemId);

    @EntityGraph(attributePaths = {"mediaAsset", "mediaAsset.sourceReference"})
    @Query("""
            SELECT itemMedia
            FROM ArchiveItemMedia itemMedia
            WHERE itemMedia.archiveItem.id = :archiveItemId
              AND (
                    NOT EXISTS (
                        SELECT figure.id
                        FROM DocumentPageFigure figure
                        WHERE figure.mediaAsset = itemMedia.mediaAsset
                    )
                    OR EXISTS (
                        SELECT figure.id
                        FROM DocumentPageFigure figure
                        WHERE figure.mediaAsset = itemMedia.mediaAsset
                          AND figure.reviewState = :approvedState
                    )
              )
            """)
    List<ArchiveItemMedia> findPublicByArchiveItemId(
            @Param("archiveItemId") Long archiveItemId,
            @Param("approvedState") FigureReviewState approvedState
    );

    @EntityGraph(attributePaths = "mediaAsset")
    List<ArchiveItemMedia> findByArchiveItem_IdInAndRoleIn(
            Collection<Long> archiveItemIds,
            Collection<MediaRole> roles
    );

    @EntityGraph(attributePaths = {
            "archiveItem",
            "mediaAsset",
            "mediaAsset.sourceReference"
    })
    @Query("""
            SELECT itemMedia
            FROM ArchiveItemMedia itemMedia
            WHERE itemMedia.archiveItem.id IN :archiveItemIds
              AND itemMedia.role IN :roles
              AND (
                    NOT EXISTS (
                        SELECT figure.id
                        FROM DocumentPageFigure figure
                        WHERE figure.mediaAsset = itemMedia.mediaAsset
                    )
                    OR EXISTS (
                        SELECT figure.id
                        FROM DocumentPageFigure figure
                        WHERE figure.mediaAsset = itemMedia.mediaAsset
                          AND figure.reviewState = :approvedState
                    )
              )
            """)
    List<ArchiveItemMedia> findPublicByArchiveItemIdsAndRoles(
            @Param("archiveItemIds") Collection<Long> archiveItemIds,
            @Param("roles") Collection<MediaRole> roles,
            @Param("approvedState") FigureReviewState approvedState
    );

    @EntityGraph(attributePaths = "mediaAsset")
    List<ArchiveItemMedia> findByArchiveItem_IdInAndRoleInAndMediaAsset_MediaType(
            Collection<Long> archiveItemIds,
            Collection<MediaRole> roles,
            MediaType mediaType
    );

    @EntityGraph(attributePaths = {
            "archiveItem",
            "mediaAsset",
            "mediaAsset.sourceReference"
    })
    @Query("""
            SELECT itemMedia
            FROM ArchiveItemMedia itemMedia
            WHERE itemMedia.archiveItem.id IN :archiveItemIds
              AND itemMedia.role IN :roles
              AND itemMedia.mediaAsset.mediaType = :mediaType
              AND (
                    NOT EXISTS (
                        SELECT figure.id
                        FROM DocumentPageFigure figure
                        WHERE figure.mediaAsset = itemMedia.mediaAsset
                    )
                    OR EXISTS (
                        SELECT figure.id
                        FROM DocumentPageFigure figure
                        WHERE figure.mediaAsset = itemMedia.mediaAsset
                          AND figure.reviewState = :approvedState
                    )
              )
            """)
    List<ArchiveItemMedia> findPublicByArchiveItemIdsRolesAndMediaType(
            @Param("archiveItemIds") Collection<Long> archiveItemIds,
            @Param("roles") Collection<MediaRole> roles,
            @Param("mediaType") MediaType mediaType,
            @Param("approvedState") FigureReviewState approvedState
    );

    List<ArchiveItemMedia> findByArchiveItemIdAndRole(
            Long archiveItemId,
            MediaRole role
    );

    boolean existsByArchiveItem_Id(Long archiveItemId);

    List<ArchiveItemMedia> findByMediaAssetId(Long mediaAssetId);

    boolean existsByMediaAsset_Id(Long mediaAssetId);

    boolean existsByArchiveItem_IdAndRole(Long archiveItemId, MediaRole role);
}
