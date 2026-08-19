package fmi.ethnowear.persistence.jpa.repository.document;

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
}
