package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentPagePreviewMediaProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentPageMediaRepository extends JpaRepository<DocumentPageMedia, Long> {

    @EntityGraph(attributePaths = "mediaAsset")
    List<DocumentPageMedia> findByDocumentPage_IdOrderByDisplayOrderAscIdAsc(Long documentPageId);

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
}
