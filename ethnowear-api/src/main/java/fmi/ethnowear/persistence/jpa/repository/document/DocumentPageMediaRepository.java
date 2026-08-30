package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.domain.model.media.MediaStorageState;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentPagePreviewMediaProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.Set;
import fmi.ethnowear.domain.model.media.MediaOrigin;

public interface DocumentPageMediaRepository extends JpaRepository<DocumentPageMedia, Long> {

    @EntityGraph(attributePaths = "mediaAsset")
    List<DocumentPageMedia> findByDocumentPage_IdOrderByDisplayOrderAscIdAsc(Long documentPageId);

    @EntityGraph(attributePaths = "mediaAsset")
    @Query("""
            SELECT pageMedia
            FROM DocumentPageMedia pageMedia
            WHERE pageMedia.documentPage.id = :documentPageId
            AND pageMedia.mediaAsset.storageState = :storageState
            ORDER BY pageMedia.displayOrder ASC, pageMedia.id ASC
            """)
    List<DocumentPageMedia> findAvailableByDocumentPageId(
            @Param("documentPageId") Long documentPageId,
            @Param("storageState") MediaStorageState storageState
    );

    @EntityGraph(attributePaths = "mediaAsset")
    Optional<DocumentPageMedia>
    findByDocumentPage_IdAndPreferredOcrInputTrue(Long documentPageId);

    @Query("""
            SELECT
                pageMedia.documentPage.id AS documentPageId,
                pageMedia.mediaAsset.id AS mediaAssetId,
                pageMedia.renditionType AS renditionType,
                pageMedia.preferredOcrInput AS preferredOcrInput,
                pageMedia.displayOrder AS displayOrder
            FROM DocumentPageMedia pageMedia
            WHERE pageMedia.documentPage.id IN :documentPageIds
            AND pageMedia.mediaAsset.storageState = fmi.ethnowear.domain.model.media.MediaStorageState.AVAILABLE
            ORDER BY
                pageMedia.documentPage.id,
                pageMedia.displayOrder,
                pageMedia.id
            """)
    List<DocumentPagePreviewMediaProjection> findPreviewCandidates(
            @Param("documentPageIds") Collection<Long> documentPageIds
    );

    Optional<DocumentPageMedia> findByIdAndDocumentPage_Id(
            Long mediaId,
            Long documentPageId
    );

    @Query("""
        SELECT pageMedia.documentPage.id
        FROM DocumentPageMedia pageMedia
        WHERE pageMedia.documentPage.id IN :pageIds
          AND pageMedia.renditionType = :renditionType
        """)
    List<Long> findPageIdsWithRendition(
            @Param("pageIds") Collection<Long> pageIds,
            @Param("renditionType") DocumentPageRenditionType renditionType
    );

    @EntityGraph(attributePaths = "mediaAsset")
    Optional<DocumentPageMedia>
    findByDocumentPage_IdAndRenditionTypeAndProducingJob_IdAndProducingAttempt(
            Long pageId,
            DocumentPageRenditionType renditionType,
            Long producingJobId,
            Integer producingAttempt
    );

    @EntityGraph(attributePaths = {
            "documentPage",
            "mediaAsset"
    })
    List<DocumentPageMedia>
    findByDocumentPage_IdInAndPreferredOcrInputTrue(
            Collection<Long> pageIds
    );

    @EntityGraph(attributePaths = {
            "mediaAsset",
            "documentPage",
            "documentPage.document",
            "documentPage.document.source",
            "documentPage.sourceReference"
    })
    List<DocumentPageMedia> findDocumentLinksByMediaAsset_IdIn(
            Collection<Long> mediaAssetIds
    );

    boolean existsByMediaAsset_Id(Long mediaAssetId);

    @EntityGraph(attributePaths = "mediaAsset")
    List<DocumentPageMedia> findByProducingJob_IdInOrderByProducingJob_IdAscIdAsc(
            Collection<Long> producingJobIds
    );

    @EntityGraph(attributePaths = {
            "mediaAsset",
            "documentPage"
    })
    @Query("""
            SELECT pageMedia
            FROM DocumentPageMedia pageMedia
            WHERE pageMedia.documentPage.document.id = :documentId
            AND pageMedia.original = false
            AND pageMedia.renditionType IN :renditionTypes
            AND pageMedia.mediaAsset.origin = :origin
            AND pageMedia.mediaAsset.storageState = fmi.ethnowear.domain.model.media.MediaStorageState.AVAILABLE
            AND (
                :retentionDeadline IS NULL
                OR pageMedia.mediaAsset.retentionUntil <= :retentionDeadline
            )
            ORDER BY pageMedia.id
            """)
    List<DocumentPageMedia> findCleanupCandidates(
            @Param("documentId") Long documentId,
            @Param("renditionTypes") Set<DocumentPageRenditionType> renditionTypes,
            @Param("origin") MediaOrigin origin,
            @Param("retentionDeadline") LocalDateTime retentionDeadline
    );

    @EntityGraph(attributePaths = "documentPage")
    List<DocumentPageMedia> findByMediaAsset_Id(Long mediaAssetId);
}
