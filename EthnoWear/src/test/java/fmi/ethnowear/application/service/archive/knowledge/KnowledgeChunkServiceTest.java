package fmi.ethnowear.application.service.archive.knowledge;

import fmi.ethnowear.testutil.EntityTestUtils;

import fmi.ethnowear.application.dto.archive.knowledge.KnowledgeChunkDetails;
import fmi.ethnowear.application.dto.archive.knowledge.KnowledgeChunkWriteDto;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.indexing.SourceTextType;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeChunkServiceTest {

    private static final String CONTENT = "Test knowledge content";
    private static final String CONTENT_HASH =
            "42cacd59e30484b4dc213aedb3f7d47d14e748c457ea1efc8f30343d05ff9339";

    @Test
    void rejectsSemanticChunkWithoutOntologyIdentity() {
        KnowledgeChunkService service = service(rejectingRepository());
        KnowledgeChunkWriteDto input = input(KnowledgeChunkType.TECHNIQUE, null, null, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(input)
        );

        assertEquals(
                "Ontology identity is required for this knowledge chunk type",
                exception.getMessage()
        );
    }

    @Test
    void rejectsSourceExcerptWithoutReference() {
        KnowledgeChunkService service = service(rejectingRepository());
        KnowledgeChunkWriteDto input = input(KnowledgeChunkType.SOURCE_EXCERPT, null, null, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(input)
        );

        assertEquals("Source excerpt requires a source reference", exception.getMessage());
    }

    @Test
    void rejectsGeneratedChunkTypeFromOrdinaryAdministration() {
        KnowledgeChunkService service = service(rejectingRepository());
        KnowledgeChunkWriteDto input = input(KnowledgeChunkType.BOOK_EXCERPT, null, null, null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(input)
        );

        assertEquals(
                "Generated knowledge chunk types cannot be created through ordinary administration",
                exception.getMessage()
        );
    }

    @Test
    void createsManualChunkWithSafeDefaultsAndContentHash() {
        KnowledgeChunkService service = service(savingRepository());

        KnowledgeChunkDetails details = service.create(
                input(KnowledgeChunkType.GENERAL, null, null, null)
        );

        assertEquals(KnowledgeChunkType.GENERAL, details.chunkType());
        assertEquals("bg", details.language());
        assertEquals(CONTENT, details.content());
        assertEquals(CONTENT_HASH, details.contentHash());
        assertEquals(SourceTextType.MANUAL_EXCERPT, details.sourceTextType());
        assertEquals(ReviewState.REVIEW_REQUIRED, details.reviewState());
        assertEquals(TranscriptionApprovalState.NOT_REQUIRED, details.transcriptionApprovalState());
        assertEquals(ProvenanceTrustState.UNKNOWN, details.provenanceTrustState());
        assertEquals(IndexingState.NOT_ELIGIBLE, details.indexingState());
        assertNull(details.vectorPointId());
    }

    @Test
    void changingIndexedContentMarksChunkOutdated() {
        KnowledgeChunk existing = new KnowledgeChunk();
        EntityTestUtils.setId(existing, 17L);
        existing.setContent("Old content");
        existing.setContentHash("old-hash");
        existing.setIndexingState(IndexingState.INDEXED);
        existing.setIndexedContentHash("old-hash");
        existing.setIndexingError("old error");

        KnowledgeChunkService service = service(existingRepository(existing));
        KnowledgeChunkDetails details = service.update(
                17L,
                input(KnowledgeChunkType.GENERAL, null, null, null)
        );

        assertEquals(CONTENT_HASH, details.contentHash());
        assertEquals(IndexingState.OUTDATED, details.indexingState());
        assertNull(details.indexingError());
        assertEquals("old-hash", details.indexedContentHash());
    }

    @Test
    void changingNonEligibleContentKeepsChunkNonEligible() {
        KnowledgeChunk existing = new KnowledgeChunk();
        EntityTestUtils.setId(existing, 18L);
        existing.setContent("Old manual content");
        existing.setContentHash("old-hash");

        KnowledgeChunkService service = service(existingRepository(existing));
        KnowledgeChunkDetails details = service.update(
                18L,
                input(KnowledgeChunkType.GENERAL, null, null, null)
        );

        assertEquals(CONTENT_HASH, details.contentHash());
        assertEquals(IndexingState.NOT_ELIGIBLE, details.indexingState());
    }

    private KnowledgeChunkService service(KnowledgeChunkRepository repository) {
        return new KnowledgeChunkService(
                repository,
                null,
                new KnowledgeChunkMapper()
        );
    }

    private KnowledgeChunkWriteDto input(
            KnowledgeChunkType chunkType,
            String ontologyIri,
            String ontologyLocalName,
            Long sourceReferenceId
    ) {
        return new KnowledgeChunkWriteDto(
                chunkType,
                ontologyIri,
                ontologyLocalName,
                "bg",
                CONTENT,
                sourceReferenceId
        );
    }

    private KnowledgeChunkRepository rejectingRepository() {
        return repository((proxy, method, arguments) -> {
            throw new AssertionError("Unexpected repository call: " + method.getName());
        });
    }

    private KnowledgeChunkRepository savingRepository() {
        return repository((proxy, method, arguments) -> {
            if (method.getName().equals("save"))
                return arguments[0];

            throw new AssertionError("Unexpected repository call: " + method.getName());
        });
    }

    private KnowledgeChunkRepository existingRepository(KnowledgeChunk existing) {
        return repository((proxy, method, arguments) -> switch (method.getName()) {
            case "findById" -> Optional.of(existing);
            case "save" -> arguments[0];
            default -> throw new AssertionError("Unexpected repository call: " + method.getName());
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
