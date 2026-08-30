package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import jakarta.persistence.LockModeType;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT document FROM Document document WHERE document.id = :documentId")
    Optional<Document> findByIdForUpdate(
            @Param("documentId") Long documentId
    );

    @EntityGraph(attributePaths = {
            "source",
            "defaultSourceReference",
            "originalMediaAsset",
            "thumbnailMediaAsset"
    })
    @Query(
            value = """
                    SELECT document
                    FROM Document document
                    LEFT JOIN document.source source
                    WHERE (
                        :searchText IS NULL
                        OR LOWER(document.title) LIKE LOWER(
                            CONCAT('%', :searchText, '%')
                        )
                        OR LOWER(document.author) LIKE LOWER(
                            CONCAT('%', :searchText, '%')
                        )
                        OR LOWER(document.publisher) LIKE LOWER(
                            CONCAT('%', :searchText, '%')
                        )
                        OR LOWER(source.title) LIKE LOWER(
                            CONCAT('%', :searchText, '%')
                        )
                    )
                    AND (
                        :documentType IS NULL
                        OR document.documentType = :documentType
                    )
                    AND (
                        :provenanceStatus IS NULL
                        OR document.provenanceStatus = :provenanceStatus
                    )
                    AND (
                        :provenanceTrustState IS NULL
                        OR document.provenanceTrustState = :provenanceTrustState
                    )
                    AND (
                        :processingState IS NULL
                        OR document.processingState = :processingState
                    )
                    AND (
                        :reviewState IS NULL
                        OR document.reviewState = :reviewState
                    )
                    AND (
                        :indexingState IS NULL
                        OR document.indexingState = :indexingState
                    )
                    AND (
                        :language IS NULL
                        OR LOWER(document.language) = LOWER(:language)
                    )
                    AND (
                        :sourceId IS NULL
                        OR source.id = :sourceId
                    )
                    """,
            countQuery = """
                    SELECT COUNT(document)
                    FROM Document document
                    LEFT JOIN document.source source
                    WHERE (
                        :searchText IS NULL
                        OR LOWER(document.title) LIKE LOWER(
                            CONCAT('%', :searchText, '%')
                        )
                        OR LOWER(document.author) LIKE LOWER(
                            CONCAT('%', :searchText, '%')
                        )
                        OR LOWER(document.publisher) LIKE LOWER(
                            CONCAT('%', :searchText, '%')
                        )
                        OR LOWER(source.title) LIKE LOWER(
                            CONCAT('%', :searchText, '%')
                        )
                    )
                    AND (
                        :documentType IS NULL
                        OR document.documentType = :documentType
                    )
                    AND (
                        :provenanceStatus IS NULL
                        OR document.provenanceStatus = :provenanceStatus
                    )
                    AND (
                        :provenanceTrustState IS NULL
                        OR document.provenanceTrustState = :provenanceTrustState
                    )
                    AND (
                        :processingState IS NULL
                        OR document.processingState = :processingState
                    )
                    AND (
                        :reviewState IS NULL
                        OR document.reviewState = :reviewState
                    )
                    AND (
                        :indexingState IS NULL
                        OR document.indexingState = :indexingState
                    )
                    AND (
                        :language IS NULL
                        OR LOWER(document.language) = LOWER(:language)
                    )
                    AND (
                        :sourceId IS NULL
                        OR source.id = :sourceId
                    )
                    """
    )
    Page<Document> findDocuments(
            @Param("searchText") String searchText,
            @Param("documentType") DocumentType documentType,
            @Param("provenanceStatus") ProvenanceStatus provenanceStatus,
            @Param("provenanceTrustState")
            ProvenanceTrustState provenanceTrustState,
            @Param("processingState") ProcessingState processingState,
            @Param("reviewState") ReviewState reviewState,
            @Param("indexingState") IndexingState indexingState,
            @Param("language") String language,
            @Param("sourceId") Long sourceId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "source",
            "defaultSourceReference",
            "originalMediaAsset",
            "thumbnailMediaAsset",
            "mergedIntoDocument"
    })
    @Query("""
            SELECT document
            FROM Document document
            WHERE document.id = :documentId
            """)
    Optional<Document> findDetailsById(
            @Param("documentId") Long documentId
    );

    boolean existsByOriginalMediaAsset_Id(Long mediaAssetId);

    boolean existsByThumbnailMediaAsset_Id(Long mediaAssetId);
}
