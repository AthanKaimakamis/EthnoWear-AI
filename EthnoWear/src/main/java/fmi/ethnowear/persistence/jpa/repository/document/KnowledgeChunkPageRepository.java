package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeChunkPageRepository extends JpaRepository<KnowledgeChunkPage, Long> {

    List<KnowledgeChunkPage> findByKnowledgeChunk_IdOrderByPageOrderAsc(
            Long knowledgeChunkId
    );

    @EntityGraph(attributePaths = "knowledgeChunk")
    List<KnowledgeChunkPage> findByDocumentPage_IdOrderByPageOrderAsc(
            Long documentPageId
    );
}
