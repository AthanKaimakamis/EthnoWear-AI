package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentPageIndexingStateCountProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;

public interface KnowledgeChunkPageRepository extends JpaRepository<KnowledgeChunkPage, Long> {

    List<KnowledgeChunkPage> findByKnowledgeChunk_IdOrderByPageOrderAsc(
            Long knowledgeChunkId
    );

    @EntityGraph(attributePaths = "knowledgeChunk")
    List<KnowledgeChunkPage> findByDocumentPage_IdOrderByPageOrderAsc(
            Long documentPageId
    );

    @EntityGraph(attributePaths = "knowledgeChunk")
    @Query("""
        SELECT link
        FROM KnowledgeChunkPage link
        WHERE link.documentPage.id = :documentPageId
        AND link.knowledgeChunk.supersededBy IS NULL
        ORDER BY link.pageOrder ASC, link.id ASC
        """)
    List<KnowledgeChunkPage> findCurrentByDocumentPageId(
            @Param("documentPageId") Long documentPageId
    );

    boolean existsByDocumentPage_Id(Long documentPageId);

    @EntityGraph(attributePaths = {"knowledgeChunk", "documentPage"})
    List<KnowledgeChunkPage>
    findByKnowledgeChunk_IdInOrderByKnowledgeChunk_IdAscPageOrderAsc(
            Collection<Long> knowledgeChunkIds
    );

    @Query("""
        SELECT
            link.documentPage.id AS pageId,
            chunk.indexingState AS state,
            COUNT(DISTINCT chunk.id) AS total
        FROM KnowledgeChunkPage link
        JOIN link.knowledgeChunk chunk
        WHERE link.documentPage.document.id = :documentId
        AND link.documentPage.evidenceState = fmi.ethnowear.domain.model.document.EvidenceState.ACTIVE
        AND chunk.document.id = :documentId
        AND chunk.supersededBy IS NULL
        GROUP BY
            link.documentPage.id,
            chunk.indexingState
        """)
    List<DocumentPageIndexingStateCountProjection>
    countCurrentIndexingStatesByPage(
            @Param("documentId") Long documentId
    );

    @EntityGraph(attributePaths = {
            "documentPage",
            "documentPage.document",
            "documentPage.sourceReference",
            "documentPage.sourceReference.source"
    })
    @Query("""
    SELECT link
    FROM KnowledgeChunkPage link
    WHERE link.knowledgeChunk.id IN :chunkIds
    ORDER BY link.knowledgeChunk.id, link.pageOrder
    """)
    List<KnowledgeChunkPage> findRetrievalPagesByChunkIdIn(
            @Param("chunkIds") Collection<Long> chunkIds
    );
}
