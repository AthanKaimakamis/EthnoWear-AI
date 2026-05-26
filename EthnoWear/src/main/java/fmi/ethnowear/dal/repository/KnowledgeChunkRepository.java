package fmi.ethnowear.dal.repository;

import fmi.ethnowear.application.enums.KnowledgeChunkType;
import fmi.ethnowear.dal.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByChunkType(KnowledgeChunkType chunkType);

    List<KnowledgeChunk> findByOntologyLocalName(String ontologyLocalName);

    List<KnowledgeChunk> findByOntologyLocalNameAndLanguage(String ontologyLocalName, String language);

    List<KnowledgeChunk> findBySourceReferenceId(Long sourceReferenceId);
}
