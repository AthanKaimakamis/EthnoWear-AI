package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.domain.model.document.quality.QualityStatus;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentIndexingStateCountProjection;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentProcessingStateCountProjection;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentReviewStateCountProjection;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentTranscriptionApprovalCountProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Query("""
            SELECT COUNT(page)
            FROM DocumentPage page
            WHERE page.document.id = :documentId
            AND page.evidenceState <> fmi.ethnowear.domain.model.document.EvidenceState.RETIRED
            """)
    long countByDocument_Id(@Param("documentId") Long documentId);

    @EntityGraph(attributePaths = {
            "document",
            "sourceReference",
            "currentQualityAssessment"
    })
    @Query(
            value = """
                    SELECT page
                    FROM DocumentPage page
                    WHERE page.document.id = :documentId
                    AND page.evidenceState <> fmi.ethnowear.domain.model.document.EvidenceState.RETIRED
                    AND (:qualityStatus IS NULL OR page.currentQualityStatus = :qualityStatus)
                    AND (:minimumScore IS NULL OR page.currentQualityScore >= :minimumScore)
                    AND (:maximumScore IS NULL OR page.currentQualityScore <= :maximumScore)
                    """,
            countQuery = """
                    SELECT COUNT(page)
                    FROM DocumentPage page
                    WHERE page.document.id = :documentId
                    AND page.evidenceState <> fmi.ethnowear.domain.model.document.EvidenceState.RETIRED
                    AND (:qualityStatus IS NULL OR page.currentQualityStatus = :qualityStatus)
                    AND (:minimumScore IS NULL OR page.currentQualityScore >= :minimumScore)
                    AND (:maximumScore IS NULL OR page.currentQualityScore <= :maximumScore)
                    """
    )
    Page<DocumentPage> findDocumentPages(
            @Param("documentId") Long documentId,
            @Param("qualityStatus") QualityStatus qualityStatus,
            @Param("minimumScore") java.math.BigDecimal minimumScore,
            @Param("maximumScore") java.math.BigDecimal maximumScore,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "document",
            "sourceReference",
            "currentQualityAssessment"
    })
    @Query("""
            SELECT page
            FROM DocumentPage page
            WHERE page.document.id = :documentId
            AND page.evidenceState <> fmi.ethnowear.domain.model.document.EvidenceState.RETIRED
            ORDER BY page.pageSequence ASC
            """)
    Page<DocumentPage> findByDocument_IdOrderByPageSequenceAsc(
            @Param("documentId") Long documentId,
            Pageable pageable
    );

    @Query("""
            SELECT page
            FROM DocumentPage page
            WHERE page.document.id = :documentId
            AND page.evidenceState <> fmi.ethnowear.domain.model.document.EvidenceState.RETIRED
            ORDER BY page.pageSequence ASC
            """)
    List<DocumentPage> findByDocument_IdOrderByPageSequenceAsc(
            @Param("documentId") Long documentId
    );

    @Query("""
            SELECT page
            FROM DocumentPage page
            WHERE page.document.id = :documentId
            AND page.evidenceState = fmi.ethnowear.domain.model.document.EvidenceState.ACTIVE
            ORDER BY page.pageSequence ASC, page.id ASC
            """)
    List<DocumentPage> findActiveByDocumentIdOrderByPageSequence(
            @Param("documentId") Long documentId
    );

    List<DocumentPage> findByDocument_IdAndSourceReferenceIsNullOrderByPageSequenceAsc(
            Long documentId
    );

    @Query("""
            SELECT
                page.document.id AS documentId,
                page.processingState AS state,
                COUNT(page.id) AS total
            FROM DocumentPage page
            WHERE page.document.id IN :documentIds
            AND page.evidenceState <> fmi.ethnowear.domain.model.document.EvidenceState.RETIRED
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
            AND page.evidenceState <> fmi.ethnowear.domain.model.document.EvidenceState.RETIRED
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
            AND page.evidenceState <> fmi.ethnowear.domain.model.document.EvidenceState.RETIRED
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
            AND page.evidenceState <> fmi.ethnowear.domain.model.document.EvidenceState.RETIRED
            GROUP BY
                page.document.id,
                page.indexingState
            """)
    List<DocumentIndexingStateCountProjection> countIndexingStates(
            @Param("documentIds") Collection<Long> documentIds
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT page
        FROM DocumentPage page
        WHERE page.id = :pageId
        """)
    Optional<DocumentPage> findByIdForUpdate(
            @Param("pageId") Long pageId
    );

    boolean existsByIdAndDocument_Id(
            Long pageId,
            Long documentId
    );

    boolean existsByDocument_IdAndTranscriptionApprovalState(
            Long documentId,
            TranscriptionApprovalState approvalState
    );

    @EntityGraph(attributePaths = {"document", "sourceReference"})
    @Query("""
            SELECT page
            FROM DocumentPage page
            WHERE page.document.id = :documentId
            ORDER BY page.pageSequence ASC, page.id ASC
            """)
    List<DocumentPage> findChunkGenerationCandidates(
            @Param("documentId") Long documentId
    );
}
