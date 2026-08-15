package fmi.ethnowear.application.service.archive.knowledge;

import fmi.ethnowear.application.dto.archive.knowledge.KnowledgeChunkDetails;
import fmi.ethnowear.application.dto.archive.knowledge.KnowledgeChunkWriteDto;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeChunkServiceTest {

    @Test
    void rejectsSemanticChunkWithoutOntologyIdentity() {
        KnowledgeChunkService service = service(rejectingRepository());
        KnowledgeChunkWriteDto input = input(KnowledgeChunkType.TECHNIQUE, null, null, null, null, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(input)
        );

        assertEquals("Ontology identity is required for this knowledge chunk type", exception.getMessage());
    }

    @Test
    void rejectsSourceExcerptWithoutReference() {
        KnowledgeChunkService service = service(rejectingRepository());
        KnowledgeChunkWriteDto input = input(KnowledgeChunkType.SOURCE_EXCERPT, null, null, null, null, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(input)
        );

        assertEquals("Source excerpt requires a source reference", exception.getMessage());
    }

    @Test
    void rejectsIncompleteEmbeddingIdentity() {
        KnowledgeChunkService service = service(rejectingRepository());
        KnowledgeChunkWriteDto input = input(KnowledgeChunkType.GENERAL, null, null, "nomic-embed-text", null, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(input)
        );

        assertEquals("Embedding model and ID must be provided together", exception.getMessage());
    }

    @Test
    void createsGeneralChunkWithoutOptionalRelationships() {
        KnowledgeChunkService service = service(savingRepository());

        KnowledgeChunkDetails details = service.create(
                input(KnowledgeChunkType.GENERAL, null, null, null, null, null)
        );

        assertEquals(KnowledgeChunkType.GENERAL, details.chunkType());
        assertEquals("bg", details.language());
        assertEquals("Test knowledge content", details.content());
    }

    private KnowledgeChunkService service(KnowledgeChunkRepository repository) {
        return new KnowledgeChunkService(repository, null, new KnowledgeChunkMapper());
    }

    private KnowledgeChunkWriteDto input(KnowledgeChunkType chunkType, String ontologyIri,
                                         String ontologyLocalName, String embeddingModel,
                                         String embeddingId, Long sourceReferenceId) {
        return new KnowledgeChunkWriteDto(
                chunkType,
                ontologyIri,
                ontologyLocalName,
                "bg",
                "Test knowledge content",
                sourceReferenceId,
                embeddingModel,
                embeddingId
        );
    }

    private KnowledgeChunkRepository rejectingRepository() {
        return repository((proxy, method, arguments) -> {
            throw new AssertionError("Unexpected repository call: " + method.getName());
        });
    }

    private KnowledgeChunkRepository savingRepository() {
        return repository((proxy, method, arguments) -> {
            if(method.getName().equals("save"))
                return (KnowledgeChunk) arguments[0];

            throw new AssertionError("Unexpected repository call: " + method.getName());
        });
    }

    private KnowledgeChunkRepository repository(java.lang.reflect.InvocationHandler handler) {
        return (KnowledgeChunkRepository) Proxy.newProxyInstance(
                KnowledgeChunkRepository.class.getClassLoader(),
                new Class<?>[]{KnowledgeChunkRepository.class},
                handler
        );
    }
}
