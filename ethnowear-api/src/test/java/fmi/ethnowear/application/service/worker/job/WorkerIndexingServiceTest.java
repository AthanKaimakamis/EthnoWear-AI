package fmi.ethnowear.application.service.worker.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.worker.indexing.WorkerIndexResultCommand;
import fmi.ethnowear.application.exception.UnprocessableDocumentEvidenceException;
import fmi.ethnowear.application.exception.WorkerClaimExpiredException;
import fmi.ethnowear.application.exception.WorkerIndexResultConflictException;
import fmi.ethnowear.application.exception.WorkerManifestConflictException;
import fmi.ethnowear.application.exception.WorkerPayloadTooLargeException;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.EvidenceState;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.persistence.jpa.repository.document.KnowledgeChunkPageRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.WorkerTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerIndexingServiceTest {

    @Test
    void returnsEligibleAuthoritativeContext() {
        Fixture fixture = fixture();

        var context = fixture.service().context(11L, credentials());

        assertEquals(10001L, context.knowledgeChunkId());
        assertEquals("Одобрен текст", context.content());
        assertEquals(fixture.chunk().getContentHash(), context.contentHash());
        assertEquals("bg", context.language());
        assertEquals(KnowledgeChunkType.BOOK_EXCERPT, context.chunkType());
        assertEquals(7L, context.documentId());
        assertEquals(51L, context.sourceReferenceId());
        assertEquals(ProvenanceTrustState.VERIFIED, context.provenanceTrustState());
        assertEquals(
                TranscriptionApprovalState.APPROVED,
                context.transcriptionApprovalState()
        );
    }

    @Test
    void rejectsSupersededUnapprovedIneligibleAndStaleEvidence() {
        Fixture fixture = fixture();
        KnowledgeChunk replacement = new KnowledgeChunk();
        EntityTestUtils.setId(replacement, 10002L);
        fixture.chunk().setSupersededBy(replacement);
        assertThrows(
                UnprocessableDocumentEvidenceException.class,
                () -> fixture.service().context(11L, credentials())
        );

        fixture.chunk().setSupersededBy(null);
        fixture.chunk().setTranscriptionApprovalState(
                TranscriptionApprovalState.PENDING
        );
        assertThrows(
                UnprocessableDocumentEvidenceException.class,
                () -> fixture.service().context(11L, credentials())
        );

        fixture.chunk().setTranscriptionApprovalState(
                TranscriptionApprovalState.APPROVED
        );
        fixture.chunk().setProvenanceTrustState(ProvenanceTrustState.UNTRUSTED);
        assertThrows(
                UnprocessableDocumentEvidenceException.class,
                () -> fixture.service().context(11L, credentials())
        );

        fixture.chunk().setProvenanceTrustState(ProvenanceTrustState.VERIFIED);
        fixture.page().setReviewState(ReviewState.REJECTED);
        assertThrows(
                UnprocessableDocumentEvidenceException.class,
                () -> fixture.service().context(11L, credentials())
        );

        fixture.page().setReviewState(ReviewState.APPROVED);
        fixture.chunk().setContent("changed");
        assertThrows(
                UnprocessableDocumentEvidenceException.class,
                () -> fixture.service().context(11L, credentials())
        );
    }

    @Test
    void acceptsIdenticalResultRejectsConflictAndStaleHash() {
        Fixture fixture = fixture();
        WorkerIndexResultCommand command = command(fixture.chunk());

        assertFalse(fixture.service().accept(11L, credentials(), command).existing());
        assertTrue(fixture.service().accept(11L, credentials(), command).existing());

        WorkerIndexResultCommand conflict = new WorkerIndexResultCommand(
                command.contentHash(),
                command.embeddingModel(),
                command.embeddingDimensions(),
                command.vectorCollection(),
                "99999"
        );
        assertThrows(
                WorkerIndexResultConflictException.class,
                () -> fixture.service().accept(11L, credentials(), conflict)
        );

        fixture.job().setParametersJson(null);
        WorkerIndexResultCommand stale = new WorkerIndexResultCommand(
                "0".repeat(64),
                command.embeddingModel(),
                command.embeddingDimensions(),
                command.vectorCollection(),
                command.vectorPointId()
        );
        assertThrows(
                WorkerManifestConflictException.class,
                () -> fixture.service().accept(11L, credentials(), stale)
        );
    }

    @Test
    void requiresAcceptedResultAndCompletesIdempotently() {
        Fixture fixture = fixture();

        assertThrows(
                UnprocessableDocumentEvidenceException.class,
                () -> fixture.service().complete(fixture.job(), credentials())
        );

        WorkerIndexResultCommand command = command(fixture.chunk());
        fixture.service().accept(11L, credentials(), command);
        var completed = fixture.service().complete(fixture.job(), credentials());
        var repeated = fixture.service().complete(fixture.job(), credentials());

        assertFalse(completed.existing());
        assertTrue(repeated.existing());
        assertEquals(JobStatus.SUCCEEDED, fixture.job().getStatus());
        assertEquals(IndexingState.INDEXED, fixture.chunk().getIndexingState());
        assertEquals("bge-m3", fixture.chunk().getEmbeddingModel());
        assertEquals(1024, fixture.chunk().getEmbeddingDimensions());
        assertEquals(
                "ethnowear_chunks_bge_m3_v1",
                fixture.chunk().getVectorCollection()
        );
        assertEquals("10001", fixture.chunk().getVectorPointId());
        assertEquals(
                fixture.chunk().getContentHash(),
                fixture.chunk().getIndexedContentHash()
        );
        assertNotNull(fixture.chunk().getIndexedAt());
        assertNull(fixture.chunk().getIndexingError());
    }

    @Test
    void preservesVectorIdentityOnFailureAndCancellation() {
        Fixture fixture = fixture();
        fixture.chunk().setVectorPointId("existing-point");
        fixture.chunk().setVectorCollection("existing-collection");
        fixture.service().recordFailure(fixture.job(), "Embedding failed safely");

        assertEquals(IndexingState.FAILED, fixture.chunk().getIndexingState());
        assertEquals("Embedding failed safely", fixture.chunk().getIndexingError());
        assertEquals("existing-point", fixture.chunk().getVectorPointId());
        assertEquals("existing-collection", fixture.chunk().getVectorCollection());

        fixture.service().recordCancellation(fixture.job());
        assertEquals("Indexing was cancelled", fixture.chunk().getIndexingError());
        assertEquals("existing-point", fixture.chunk().getVectorPointId());
        org.mockito.Mockito.verify(
                fixture.reconciler(),
                org.mockito.Mockito.times(2)
        ).reconcileLocked(fixture.job().getDocument());
    }

    @Test
    void enforcesLeaseAndConfiguredBounds() {
        Fixture fixture = fixture();
        fixture.job().setClaimExpiresAt(
                java.time.LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
        assertThrows(
                WorkerClaimExpiredException.class,
                () -> fixture.service().context(11L, credentials())
        );

        fixture.job().setClaimExpiresAt(
                java.time.LocalDateTime.ofInstant(
                        NOW.plusSeconds(120),
                        ZoneOffset.UTC
                )
        );
        fixture.chunk().setContent("x".repeat(10001));
        fixture.chunk().setContentHash(ContentHashUtils.sha256(fixture.chunk().getContent()));
        assertThrows(
                WorkerPayloadTooLargeException.class,
                () -> fixture.service().context(11L, credentials())
        );
    }

    @Test
    void rejectsResultOutsideTheConfiguredEmbeddingContract() {
        Fixture fixture = fixture();
        WorkerIndexResultCommand command = command(fixture.chunk());

        WorkerIndexResultCommand oversizedModel = new WorkerIndexResultCommand(
                command.contentHash(),
                "x".repeat(101),
                command.embeddingDimensions(),
                command.vectorCollection(),
                command.vectorPointId()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().accept(
                        11L,
                        credentials(),
                        oversizedModel
                )
        );

        WorkerIndexResultCommand wrongDimensions = new WorkerIndexResultCommand(
                command.contentHash(),
                command.embeddingModel(),
                2048,
                command.vectorCollection(),
                command.vectorPointId()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service().accept(
                        11L,
                        credentials(),
                        wrongDimensions
                )
        );
    }

    private Fixture fixture() {
        Document document = document(7L);
        document.setDocumentType(DocumentType.SCANNED_BOOK);

        SourceReference sourceReference = new SourceReference();
        EntityTestUtils.setId(sourceReference, 51L);

        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 21L);
        page.setDocument(document);
        page.setSourceReference(sourceReference);
        page.setEvidenceState(EvidenceState.ACTIVE);
        page.setProcessingState(ProcessingState.COMPLETED);
        page.setReviewState(ReviewState.APPROVED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.APPROVED);
        page.setProvenanceTrustState(ProvenanceTrustState.VERIFIED);
        page.setIndexingState(IndexingState.PENDING);
        page.setCorrectedText("Одобрен текст");
        page.setCorrectedTextHash(ContentHashUtils.sha256(page.getCorrectedText()));

        KnowledgeChunk chunk = new KnowledgeChunk();
        EntityTestUtils.setId(chunk, 10001L);
        chunk.setDocument(document);
        chunk.setSourceReference(sourceReference);
        chunk.setChunkType(KnowledgeChunkType.BOOK_EXCERPT);
        chunk.setLanguage("bg");
        chunk.setContent("Одобрен текст");
        chunk.setContentHash(ContentHashUtils.sha256(chunk.getContent()));
        chunk.setReviewState(ReviewState.APPROVED);
        chunk.setTranscriptionApprovalState(TranscriptionApprovalState.APPROVED);
        chunk.setProvenanceTrustState(ProvenanceTrustState.VERIFIED);
        chunk.setIndexingState(IndexingState.PENDING);

        KnowledgeChunkPage link = new KnowledgeChunkPage();
        link.setKnowledgeChunk(chunk);
        link.setDocumentPage(page);
        link.setPageOrder(0);

        DocumentProcessingJob job = activePageExtractionJob(11L, document);
        job.setJobType(JobType.INDEX_CHUNK);
        job.setKnowledgeChunk(chunk);
        job.assignActiveJobKey("INDEX_CHUNK:CHUNK:10001");

        DocumentProcessingJobRepository jobs = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("saveAndFlush"))
                        return arguments[0];

                    throw new AssertionError(
                            "Unexpected job repository call: " + method.getName()
                    );
                }
        );
        AtomicReference<KnowledgeChunk> stored = new AtomicReference<>(chunk);
        KnowledgeChunkRepository chunks = proxy(
                KnowledgeChunkRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByIdForUpdate" -> Optional.ofNullable(stored.get());
                    case "save", "saveAndFlush" -> {
                        stored.set((KnowledgeChunk) arguments[0]);
                        yield arguments[0];
                    }
                    default -> throw new AssertionError(
                            "Unexpected chunk repository call: " + method.getName()
                    );
                }
        );
        KnowledgeChunkPageRepository links = proxy(
                KnowledgeChunkPageRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals(
                            "findByKnowledgeChunk_IdOrderByPageOrderAsc"
                    ))
                        return List.of(link);

                    throw new AssertionError(
                            "Unexpected link repository call: " + method.getName()
                    );
                }
        );

        var reconciler = indexingStateReconciler(document);

        return new Fixture(
                new WorkerIndexingService(
                        loader(job),
                        jobs,
                        chunks,
                        links,
                        indexingProperties(),
                        new ObjectMapper(),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        fmi.ethnowear.support.ManagementEventTestSupport.events(),
                        reconciler
                ),
                job,
                chunk,
                page,
                reconciler
        );
    }

    private fmi.ethnowear.application.service.document.indexing.DocumentIndexingStateReconciler indexingStateReconciler(
            Document document
    ) {
        var reconciler = org.mockito.Mockito.mock(
                fmi.ethnowear.application.service.document.indexing.DocumentIndexingStateReconciler.class
        );
        org.mockito.Mockito.when(reconciler.lockDocument(document.getId()))
                .thenReturn(document);
        return reconciler;
    }

    private WorkerIndexResultCommand command(KnowledgeChunk chunk) {
        return new WorkerIndexResultCommand(
                chunk.getContentHash(),
                "bge-m3",
                1024,
                "ethnowear_chunks_bge_m3_v1",
                String.valueOf(chunk.getId())
        );
    }

    private record Fixture(
            WorkerIndexingService service,
            DocumentProcessingJob job,
            KnowledgeChunk chunk,
            DocumentPage page,
            fmi.ethnowear.application.service.document.indexing.DocumentIndexingStateReconciler reconciler
    ) {
    }
}
