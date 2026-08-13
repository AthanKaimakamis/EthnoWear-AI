package fmi.ethnowear.dal.repository;

import fmi.ethnowear.application.enums.KnowledgeChunkType;
import fmi.ethnowear.dal.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    /**
     * Finds knowledge chunks belonging to the specified chunk type.
     *
     * @param chunkType knowledge category used to filter the chunks
     * @return matching chunks, or an empty list when none exist
     */
    List<KnowledgeChunk> findByChunkType(KnowledgeChunkType chunkType);

    /**
     * Finds knowledge chunks linked to an ontology resource by its cached local name.
     *
     * @param ontologyLocalName local name of the ontology resource
     * @return matching chunks, or an empty list when none exist
     */
    List<KnowledgeChunk> findByOntologyLocalName(String ontologyLocalName);

    /**
     * Finds localized knowledge chunks linked to an ontology resource.
     *
     * @param ontologyLocalName local name of the ontology resource
     * @param language language code used to filter the chunks
     * @return matching chunks, or an empty list when none exist
     */
    List<KnowledgeChunk> findByOntologyLocalNameAndLanguage(String ontologyLocalName, String language);

    /**
     * Finds knowledge chunks supported by an exact source reference.
     *
     * @param sourceReferenceId database identifier of the source reference
     * @return matching chunks, or an empty list when none exist
     */
    List<KnowledgeChunk> findBySourceReferenceId(Long sourceReferenceId);

    /**
     * Checks whether an exact source reference supports at least one knowledge chunk.
     *
     * @param sourceReferenceId database identifier of the source reference
     * @return {@code true} when at least one knowledge chunk uses the reference
     */
    boolean existsBySourceReference_Id(Long sourceReferenceId);

    /**
     * Finds localized knowledge content linked to an ontology entity.
     * The source citation and its parent source are loaded in the same query.
     *
     * @param ontologyIri authoritative ontology IRI of the entity
     * @param language language code used to filter the content
     * @return matching chunks ordered by database identifier
     */
    @EntityGraph(attributePaths = { "sourceReference", "sourceReference.source" })
    List<KnowledgeChunk> findByOntologyIriAndLanguageOrderByIdAsc(
            String ontologyIri,
            String language
    );
}
