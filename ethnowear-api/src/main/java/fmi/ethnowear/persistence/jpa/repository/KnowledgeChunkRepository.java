package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentIndexingStateCountProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "document",
            "sourceReference",
            "archiveItem",
            "supersededBy"
    })
    @Query("SELECT chunk FROM KnowledgeChunk chunk WHERE chunk.id = :id")
    Optional<KnowledgeChunk> findByIdForUpdate(@Param("id") Long id);

    List<KnowledgeChunk> findByChunkType(KnowledgeChunkType chunkType);

    List<KnowledgeChunk> findByOntologyLocalName(String ontologyLocalName);

    boolean existsByOntologyIriIn(Collection<String> ontologyIris);

    List<KnowledgeChunk> findByOntologyLocalNameAndLanguage(
            String ontologyLocalName,
            String language
    );

    List<KnowledgeChunk> findBySourceReferenceId(Long sourceReferenceId);

    boolean existsBySourceReference_Id(Long sourceReferenceId);

    List<KnowledgeChunk>
    findByDocument_IdAndGenerationInputHashOrderByChunkOrdinalAsc(
            Long documentId,
            String generationInputHash
    );

    List<KnowledgeChunk>
    findByDocument_IdAndGenerationInputHashIsNotNullAndSupersededByIsNullOrderByChunkOrdinalAsc(
            Long documentId
    );

    @EntityGraph(attributePaths = {
            "document",
            "sourceReference",
            "supersededBy"
    })
    Page<KnowledgeChunk>
    findByDocument_IdAndGenerationInputHashIsNotNull(
            Long documentId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"sourceReference", "sourceReference.source"})
    List<KnowledgeChunk> findByOntologyIriAndLanguageOrderByIdAsc(
            String ontologyIri,
            String language
    );

    @Query("""
        SELECT
            chunk.document.id AS documentId,
            chunk.indexingState AS state,
            COUNT(DISTINCT chunk.id) AS total
        FROM KnowledgeChunkPage link
        JOIN link.knowledgeChunk chunk
        WHERE chunk.document.id IN :documentIds
        AND chunk.supersededBy IS NULL
        AND link.documentPage.evidenceState = fmi.ethnowear.domain.model.document.EvidenceState.ACTIVE
        GROUP BY
            chunk.document.id,
            chunk.indexingState
        """)
    List<DocumentIndexingStateCountProjection> countActiveIndexingStates(
            @Param("documentIds")
            Collection<Long> documentIds
    );

    @Query("""
        SELECT COUNT(DISTINCT chunk.id)
        FROM KnowledgeChunkPage link
        JOIN link.knowledgeChunk chunk
        WHERE chunk.document.id = :documentId
        AND chunk.supersededBy IS NULL
        AND chunk.indexingState <> fmi.ethnowear.domain.model.document.indexing.IndexingState.NOT_ELIGIBLE
        AND link.documentPage.evidenceState = fmi.ethnowear.domain.model.document.EvidenceState.ACTIVE
        """)
    long countCurrentEligibleByDocumentId(
            @Param("documentId") Long documentId
    );

    @Query("""
        SELECT COUNT(DISTINCT chunk.id)
        FROM KnowledgeChunkPage link
        JOIN link.knowledgeChunk chunk
        WHERE chunk.document.id = :documentId
        AND chunk.supersededBy IS NULL
        AND chunk.indexingState <> fmi.ethnowear.domain.model.document.indexing.IndexingState.NOT_ELIGIBLE
        AND chunk.indexingState <> :indexingState
        AND link.documentPage.evidenceState = fmi.ethnowear.domain.model.document.EvidenceState.ACTIVE
        """)
    long countCurrentEligibleByDocumentIdAndIndexingStateNot(
            @Param("documentId") Long documentId,
            @Param("indexingState") IndexingState indexingState
    );

    @Query("""
        SELECT chunk.document.id
        FROM KnowledgeChunk chunk
        WHERE chunk.id = :chunkId
        """)
    Optional<Long> findDocumentIdByChunkId(@Param("chunkId") Long chunkId);

    @EntityGraph(attributePaths = {
            "document",
            "document.source",
            "sourceReference",
            "sourceReference.source",
            "archiveItem",
            "supersededBy"
    })
    @Query("""
    SELECT chunk
    FROM KnowledgeChunk chunk
    WHERE chunk.id IN :chunkIds
    """)
    List<KnowledgeChunk> findRetrievalCandidatesByIdIn(
            @Param("chunkIds") Collection<Long> chunkIds
    );
}
