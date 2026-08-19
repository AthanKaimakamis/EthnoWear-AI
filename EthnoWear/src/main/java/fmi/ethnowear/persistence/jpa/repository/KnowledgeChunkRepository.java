package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentIndexingStateCountProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByChunkType(KnowledgeChunkType chunkType);

    List<KnowledgeChunk> findByOntologyLocalName(String ontologyLocalName);

    List<KnowledgeChunk> findByOntologyLocalNameAndLanguage(
            String ontologyLocalName,
            String language
    );

    List<KnowledgeChunk> findBySourceReferenceId(Long sourceReferenceId);

    boolean existsBySourceReference_Id(Long sourceReferenceId);

    @EntityGraph(attributePaths = {"sourceReference", "sourceReference.source"})
    List<KnowledgeChunk> findByOntologyIriAndLanguageOrderByIdAsc(
            String ontologyIri,
            String language
    );

    @Query("""
        SELECT
            chunk.document.id AS documentId,
            chunk.indexingState AS state,
            COUNT(chunk.id) AS total
        FROM KnowledgeChunk chunk
        WHERE chunk.document.id IN :documentIds
        AND chunk.supersededBy IS NULL
        GROUP BY
            chunk.document.id,
            chunk.indexingState
        """)
    List<DocumentIndexingStateCountProjection> countActiveIndexingStates(
            @Param("documentIds")
            Collection<Long> documentIds
    );
}
