package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentIndexingStateCountProjection;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentProcessingStateCountProjection;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentReviewStateCountProjection;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentTranscriptionApprovalCountProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentPageRepository extends JpaRepository<DocumentPage, Long> {

    @EntityGraph(attributePaths = {
            "document",
            "sourceReference",
            "canonicalDocumentPage"
    })
    Optional<DocumentPage> findByIdAndDocument_Id(
            Long pageId, Long documentId
    );

    Optional<DocumentPage> findByDocument_IdAndPageSequence(
            Long documentId, Integer pageSequence
    );

    @EntityGraph(attributePaths = {
            "document",
            "sourceReference"
    })
    Page<DocumentPage> findByDocument_IdOrderByPageSequenceAsc(
            Long documentId,
            Pageable pageable
    );

    @Query("""
            SELECT
                page.document.id AS documentId,
                page.processingState AS state,
                COUNT(page.id) AS total
            FROM DocumentPage page
            WHERE page.document.id IN :documentIds
            GROUP BY
                page.document.id,
                page.processingState
            """)
    List<DocumentProcessingStateCountProjection> countProcessingStates(
            @Param("documentIds") Collection<Long> documentIds
    );

    @Query("""
            SELECT
                page.document.id AS documentId,
                page.reviewState AS state,
                COUNT(page.id) AS total
            FROM DocumentPage page
            WHERE page.document.id IN :documentIds
            GROUP BY
                page.document.id,
                page.reviewState
            """)
    List<DocumentReviewStateCountProjection> countReviewStates(
            @Param("documentIds") Collection<Long> documentIds
    );

    @Query("""
            SELECT
                page.document.id AS documentId,
                page.transcriptionApprovalState AS state,
                COUNT(page.id) AS total
            FROM DocumentPage page
            WHERE page.document.id IN :documentIds
            GROUP BY
                page.document.id,
                page.transcriptionApprovalState
            """)
    List<DocumentTranscriptionApprovalCountProjection> countTranscriptionApprovalStates(
            @Param("documentIds") Collection<Long> documentIds
    );

    @Query("""
            SELECT
                page.document.id AS documentId,
                page.indexingState AS state,
                COUNT(page.id) AS total
            FROM DocumentPage page
            WHERE page.document.id IN :documentIds
            GROUP BY
                page.document.id,
                page.indexingState
            """)
    List<DocumentIndexingStateCountProjection> countIndexingStates(
            @Param("documentIds") Collection<Long> documentIds
    );

    boolean existsByIdAndDocument_Id(
            Long pageId,
            Long documentId
    );
}
