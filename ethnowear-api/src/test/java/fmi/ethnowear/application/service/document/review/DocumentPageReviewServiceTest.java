package fmi.ethnowear.application.service.document.review;

import fmi.ethnowear.application.dto.document.command.review.CorrectedTextSaveCommand;
import fmi.ethnowear.application.dto.document.command.review.CorrectedTextResetCommand;
import fmi.ethnowear.application.dto.document.command.review.PageApprovalCommand;
import fmi.ethnowear.application.dto.document.command.review.PageRejectionCommand;
import fmi.ethnowear.application.exception.InvalidDocumentPageReviewTransitionException;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.application.service.document.chunk.DocumentChunkGenerationEligibilityService;
import fmi.ethnowear.application.service.document.chunk.DocumentChunkGenerationService;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewAction;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageReview;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageReviewRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.KnowledgeChunkPageRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.*;

class DocumentPageReviewServiceTest {

    @Test
    void explicitlyResetsCorrectedTextFromCurrentOcrAndSnapshotsPreviousText() {
        DocumentPage page = page();
        page.setRawOcrText("page OCR snapshot");
        page.setCorrectedText("contaminated historical correction");
        page.setCorrectedTextHash(ContentHashUtils.sha256(
                "contaminated historical correction"
        ));
        page.setReviewState(ReviewState.APPROVED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.APPROVED);
        page.setIndexingState(IndexingState.INDEXED);

        DocumentPageOcrResult currentOcr = entity(
                new DocumentPageOcrResult(),
                71L
        );
        currentOcr.setDocumentPage(page);
        currentOcr.setRawText("current append-only OCR text");
        currentOcr.setCurrent(true);

        List<DocumentPageReview> events = new ArrayList<>();
        AtomicReference<DocumentPage> savedPage = new AtomicReference<>();

        DocumentPageReviewService service = service(
                page,
                null,
                events,
                savedPage,
                currentOcr
        );

        var result = service.resetFromCurrentOcr(
                11L,
                new CorrectedTextResetCommand(true),
                "administrator"
        );

        assertEquals(ReviewAction.RESET_FROM_CURRENT_OCR, result.reviewAction());
        assertEquals(2, events.size());
        assertEquals(
                ReviewAction.APPROVAL_REVOKED,
                events.getFirst().getReviewAction()
        );
        assertEquals(
                "contaminated historical correction",
                events.get(1).getCorrectedTextSnapshot()
        );
        assertEquals(
                ContentHashUtils.sha256("contaminated historical correction"),
                events.get(1).getCorrectedTextHash()
        );
        assertEquals(
                "current append-only OCR text",
                savedPage.get().getCorrectedText()
        );
        assertEquals(
                ContentHashUtils.sha256("current append-only OCR text"),
                savedPage.get().getCorrectedTextHash()
        );
        assertEquals(ReviewState.REVIEW_REQUIRED, savedPage.get().getReviewState());
        assertEquals(
                TranscriptionApprovalState.PENDING,
                savedPage.get().getTranscriptionApprovalState()
        );
        assertEquals("page OCR snapshot", savedPage.get().getRawOcrText());
        assertTrue(currentOcr.isCurrent());
    }

    @Test
    void rejectsUnconfirmedCorrectedTextReset() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service(
                        page(),
                        null,
                        new ArrayList<>(),
                        new AtomicReference<>()
                ).resetFromCurrentOcr(
                        11L,
                        new CorrectedTextResetCommand(false),
                        "administrator"
                )
        );
    }

    @Test
    void editingApprovedTextRevokesApprovalAndInvalidatesExistingChunks() {
        DocumentPage page = page();
        page.setRawOcrText("immutable raw OCR");
        page.setCorrectedText("approved text");
        page.setCorrectedTextHash(ContentHashUtils.sha256("approved text"));
        page.setReviewState(ReviewState.APPROVED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.APPROVED);
        page.setProvenanceTrustState(ProvenanceTrustState.VERIFIED);
        page.setIndexingState(IndexingState.INDEXED);
        KnowledgeChunk chunk = entity(new KnowledgeChunk(), 31L);
        chunk.setIndexingState(IndexingState.INDEXED);
        List<DocumentPageReview> events = new ArrayList<>();
        AtomicReference<DocumentPage> savedPage = new AtomicReference<>();

        DocumentPageReviewService service = service(
                page,
                chunk,
                events,
                savedPage
        );

        var result = service.saveCorrectedText(
                11L,
                new CorrectedTextSaveCommand("new corrected text"),
                "curator"
        );

        assertEquals(2, events.size());
        assertEquals(ReviewAction.APPROVAL_REVOKED, events.getFirst().getReviewAction());
        assertEquals("approved text", events.getFirst().getCorrectedTextSnapshot());
        assertEquals(ReviewAction.CORRECTED_TEXT_SAVED, events.getLast().getReviewAction());
        assertEquals(ReviewAction.CORRECTED_TEXT_SAVED, result.reviewAction());
        assertEquals("immutable raw OCR", savedPage.get().getRawOcrText());
        assertEquals("new corrected text", savedPage.get().getCorrectedText());
        assertEquals(
                ContentHashUtils.sha256("new corrected text"),
                savedPage.get().getCorrectedTextHash()
        );
        assertEquals(ReviewState.IN_REVIEW, savedPage.get().getReviewState());
        assertEquals(
                TranscriptionApprovalState.PENDING,
                savedPage.get().getTranscriptionApprovalState()
        );
        assertEquals(ProvenanceTrustState.VERIFIED, savedPage.get().getProvenanceTrustState());
        assertEquals(IndexingState.OUTDATED, savedPage.get().getIndexingState());
        assertEquals(IndexingState.OUTDATED, chunk.getIndexingState());
    }

    @Test
    void savingUnchangedApprovedTextPreservesApprovalAndIndex() {
        DocumentPage page = page();
        page.setCorrectedText("approved text");
        page.setCorrectedTextHash(ContentHashUtils.sha256("approved text"));
        page.setReviewState(ReviewState.APPROVED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.APPROVED);
        page.setIndexingState(IndexingState.INDEXED);
        List<DocumentPageReview> events = new ArrayList<>();

        service(page, null, events, new AtomicReference<>())
                .saveCorrectedText(
                        11L,
                        new CorrectedTextSaveCommand("approved text"),
                        "curator"
                );

        assertEquals(ReviewState.APPROVED, page.getReviewState());
        assertEquals(
                TranscriptionApprovalState.APPROVED,
                page.getTranscriptionApprovalState()
        );
        assertEquals(IndexingState.INDEXED, page.getIndexingState());
        assertEquals(1, events.size());
    }

    @Test
    void approvesCompletedPageWithMatchingCorrectedTextHash() {
        DocumentPage page = page();
        page.setCorrectedText("verified transcription");
        page.setCorrectedTextHash(ContentHashUtils.sha256("verified transcription"));
        page.setReviewState(ReviewState.IN_REVIEW);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
        page.setProvenanceTrustState(ProvenanceTrustState.UNKNOWN);
        List<DocumentPageReview> events = new ArrayList<>();
        AtomicReference<DocumentPage> savedPage = new AtomicReference<>();

        var result = service(page, null, events, savedPage).approve(
                11L,
                new PageApprovalCommand("Checked against the scan"),
                "curator"
        );

        assertEquals(ReviewAction.APPROVED, result.reviewAction());
        assertEquals(ReviewState.APPROVED, savedPage.get().getReviewState());
        assertEquals(
                TranscriptionApprovalState.APPROVED,
                savedPage.get().getTranscriptionApprovalState()
        );
        assertEquals(ProvenanceTrustState.UNKNOWN, savedPage.get().getProvenanceTrustState());
        assertEquals("Checked against the scan", savedPage.get().getReviewNotes());
        assertNotNull(savedPage.get().getReviewedAt());
    }

    @Test
    void approvesPageWithoutGeneratingChunksWhenProvenanceIsIneligible() {
        DocumentPage page = page();
        page.setCorrectedText("verified transcription");
        page.setCorrectedTextHash(ContentHashUtils.sha256("verified transcription"));
        page.setReviewState(ReviewState.IN_REVIEW);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
        page.setProvenanceTrustState(ProvenanceTrustState.UNKNOWN);
        List<DocumentPageReview> events = new ArrayList<>();
        AtomicReference<DocumentPage> savedPage = new AtomicReference<>();
        DocumentChunkGenerationEligibilityService eligibility = org.mockito.Mockito.mock(
                DocumentChunkGenerationEligibilityService.class
        );
        DocumentChunkGenerationService generation = org.mockito.Mockito.mock(
                DocumentChunkGenerationService.class
        );

        service(page, null, events, savedPage, null, eligibility, generation)
                .approve(11L, null, "curator");

        assertEquals(ReviewState.APPROVED, savedPage.get().getReviewState());
        org.mockito.Mockito.verify(eligibility).isPageEligible(10L, 11L);
        org.mockito.Mockito.verifyNoInteractions(generation);
    }

    @Test
    void generatesChunksImmediatelyAfterEligiblePageApproval() {
        DocumentPage page = page();
        page.setCorrectedText("verified transcription");
        page.setCorrectedTextHash(ContentHashUtils.sha256("verified transcription"));
        page.setReviewState(ReviewState.IN_REVIEW);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
        DocumentChunkGenerationEligibilityService eligibility = org.mockito.Mockito.mock(
                DocumentChunkGenerationEligibilityService.class
        );
        DocumentChunkGenerationService generation = org.mockito.Mockito.mock(
                DocumentChunkGenerationService.class
        );
        org.mockito.Mockito.when(eligibility.isPageEligible(10L, 11L))
                .thenReturn(true);

        service(
                page,
                null,
                new ArrayList<>(),
                new AtomicReference<>(),
                null,
                eligibility,
                generation
        ).approve(11L, null, "curator");

        org.mockito.Mockito.verify(generation).generatePageIfRequired(10L, 11L);
    }

    @Test
    void approvesUneditedRawOcrWithoutRequiringTextSave() {
        DocumentPage page = page();
        page.setRawOcrText("непроменен OCR текст");
        page.setCorrectedText(null);
        page.setCorrectedTextHash(null);
        page.setReviewState(ReviewState.REVIEW_REQUIRED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
        List<DocumentPageReview> events = new ArrayList<>();
        AtomicReference<DocumentPage> savedPage = new AtomicReference<>();

        var result = service(page, null, events, savedPage).approve(
                11L,
                null,
                "curator"
        );

        assertEquals(ReviewAction.APPROVED, result.reviewAction());
        assertEquals("непроменен OCR текст", savedPage.get().getCorrectedText());
        assertEquals(
                ContentHashUtils.sha256("непроменен OCR текст"),
                savedPage.get().getCorrectedTextHash()
        );
        assertEquals("непроменен OCR текст", events.getFirst().getCorrectedTextSnapshot());
        assertEquals(ReviewState.APPROVED, savedPage.get().getReviewState());
        assertEquals(
                TranscriptionApprovalState.APPROVED,
                savedPage.get().getTranscriptionApprovalState()
        );
    }

    @Test
    void rejectsPageWithoutDeletingTextOrChangingProvenanceTrust() {
        DocumentPage page = page();
        page.setRawOcrText("raw OCR");
        page.setCorrectedText("draft correction");
        page.setCorrectedTextHash(ContentHashUtils.sha256("draft correction"));
        page.setReviewState(ReviewState.IN_REVIEW);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);
        page.setProvenanceTrustState(ProvenanceTrustState.TRUSTED);
        List<DocumentPageReview> events = new ArrayList<>();
        AtomicReference<DocumentPage> savedPage = new AtomicReference<>();

        var result = service(page, null, events, savedPage).reject(
                11L,
                new PageRejectionCommand("The transcription is incomplete"),
                "curator"
        );

        assertEquals(ReviewAction.REJECTED, result.reviewAction());
        assertEquals("raw OCR", savedPage.get().getRawOcrText());
        assertEquals("draft correction", savedPage.get().getCorrectedText());
        assertEquals(ReviewState.REJECTED, savedPage.get().getReviewState());
        assertEquals(
                TranscriptionApprovalState.REJECTED,
                savedPage.get().getTranscriptionApprovalState()
        );
        assertEquals(ProvenanceTrustState.TRUSTED, savedPage.get().getProvenanceTrustState());
    }

    @Test
    void rejectsApprovalWhenCorrectedTextHashDoesNotMatch() {
        DocumentPage page = page();
        page.setCorrectedText("changed text");
        page.setCorrectedTextHash(ContentHashUtils.sha256("old text"));

        assertThrows(
                InvalidDocumentPageReviewTransitionException.class,
                () -> service(
                        page,
                        null,
                        new ArrayList<>(),
                        new AtomicReference<>()
                ).approve(11L, new PageApprovalCommand(null), "curator")
        );
    }

    @Test
    void rejectsOversizedApprovalNotesBeforeLoadingPage() {
        DocumentPageReviewService service = service(
                page(),
                null,
                new ArrayList<>(),
                new AtomicReference<>()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.approve(
                        11L,
                        new PageApprovalCommand("x".repeat(1001)),
                        "curator"
                )
        );
    }

    private DocumentPageReviewService service(
            DocumentPage page,
            KnowledgeChunk chunk,
            List<DocumentPageReview> events,
            AtomicReference<DocumentPage> savedPage
    ) {
        return service(page, chunk, events, savedPage, null);
    }

    private DocumentPageReviewService service(
            DocumentPage page,
            KnowledgeChunk chunk,
            List<DocumentPageReview> events,
            AtomicReference<DocumentPage> savedPage,
            DocumentPageOcrResult currentOcr
    ) {
        return service(
                page,
                chunk,
                events,
                savedPage,
                currentOcr,
                org.mockito.Mockito.mock(DocumentChunkGenerationEligibilityService.class),
                org.mockito.Mockito.mock(DocumentChunkGenerationService.class)
        );
    }

    private DocumentPageReviewService service(
            DocumentPage page,
            KnowledgeChunk chunk,
            List<DocumentPageReview> events,
            AtomicReference<DocumentPage> savedPage,
            DocumentPageOcrResult currentOcr,
            DocumentChunkGenerationEligibilityService eligibility,
            DocumentChunkGenerationService generation
    ) {
        return new DocumentPageReviewService(
                pageRepository(page, savedPage),
                ocrResultRepository(currentOcr),
                reviewRepository(events),
                org.mockito.Mockito.mock(
                        fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualityAssessmentRepository.class
                ),
                org.mockito.Mockito.mock(
                        fmi.ethnowear.persistence.jpa.repository.document.DocumentPageTextSuggestionRepository.class
                ),
                invalidator(page, chunk),
                eligibility,
                generation,
                new DocumentHistoryMapper(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        );
    }

    private DocumentPageOcrResultRepository ocrResultRepository(
            DocumentPageOcrResult currentOcr
    ) {
        return proxy(
                DocumentPageOcrResultRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals(
                            "findByDocumentPage_IdAndCurrentTrue"
                    ))
                        return Optional.ofNullable(currentOcr);

                    throw new AssertionError(
                            "Unexpected OCR-result call: " + method.getName()
                    );
                }
        );
    }

    private DocumentPageRepository pageRepository(
            DocumentPage page,
            AtomicReference<DocumentPage> savedPage
    ) {
        return proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByIdForUpdate" -> Optional.of(page);
                    case "save" -> {
                        savedPage.set((DocumentPage) arguments[0]);
                        yield arguments[0];
                    }
                    default -> throw new AssertionError("Unexpected page call: " + method.getName());
                }
        );
    }

    private DocumentPageReviewRepository reviewRepository(List<DocumentPageReview> events) {
        return proxy(
                DocumentPageReviewRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "save", "saveAndFlush" -> {
                        DocumentPageReview event = (DocumentPageReview) arguments[0];
                        if(event.getId() == null)
                            EntityTestUtils.setId(event, (long) events.size() + 41L);
                        events.add(event);
                        yield event;
                    }
                    default -> throw new AssertionError("Unexpected review call: " + method.getName());
                }
        );
    }

    private DocumentPageChunkInvalidator invalidator(
            DocumentPage page,
            KnowledgeChunk chunk
    ) {
        KnowledgeChunkPageRepository links = proxy(
                KnowledgeChunkPageRepository.class,
                (ignored, method, arguments) -> {
                    if(!method.getName().equals("findCurrentByDocumentPageId"))
                        throw new AssertionError("Unexpected chunk-page call: " + method.getName());
                    if(chunk == null)
                        return List.of();
                    KnowledgeChunkPage link = new KnowledgeChunkPage();
                    link.setDocumentPage(page);
                    link.setKnowledgeChunk(chunk);
                    return List.of(link);
                }
        );
        KnowledgeChunkRepository chunks = proxy(
                KnowledgeChunkRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("saveAllAndFlush"))
                        return ((Iterable<?>) arguments[0])
                                .spliterator()
                                .getExactSizeIfKnown() == 0
                                ? List.of()
                                : List.of(chunk);
                    throw new AssertionError("Unexpected chunk call: " + method.getName());
                }
        );

        var reconciler = org.mockito.Mockito.mock(
                fmi.ethnowear.application.service.document.indexing.DocumentIndexingStateReconciler.class
        );
        org.mockito.Mockito.when(reconciler.lockDocument(
                page.getDocument().getId()
        )).thenReturn(page.getDocument());
        org.mockito.Mockito.doAnswer(ignored -> {
            if (chunk != null)
                page.setIndexingState(IndexingState.OUTDATED);
            return null;
        }).when(reconciler).reconcileLocked(page.getDocument());

        return new DocumentPageChunkInvalidator(links, chunks, reconciler);
    }

    private DocumentPage page() {
        DocumentPage page = entity(new DocumentPage(), 11L);
        page.setDocument(entity(new Document(), 10L));
        page.setProcessingState(ProcessingState.COMPLETED);
        return page;
    }

    private <T extends AppendOnlyEntity> T entity(T entity, Long id) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }
}
