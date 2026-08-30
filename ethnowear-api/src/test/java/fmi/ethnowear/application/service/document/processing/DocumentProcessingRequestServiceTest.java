package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.exception.InvalidDocumentProcessingRequestException;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.application.service.document.chunk.DocumentChunkGenerationEligibilityService;
import fmi.ethnowear.application.model.document.chunk.ChunkGenerationInput;
import fmi.ethnowear.application.model.document.chunk.EligibleChunkPage;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.*;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.RepositoryTestProxies.rejecting;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentProcessingRequestServiceTest {

    @Test
    void queuesOcrWithPreferredInputAndMarksPagePending() {
        Document document = entity(new Document(), 1L);
        DocumentPage page = entity(new DocumentPage(), 2L);
        page.setDocument(document);
        page.setProcessingState(ProcessingState.COMPLETED);
        MediaAsset media = entity(new MediaAsset(), 3L);
        DocumentPageMedia pageMedia = new DocumentPageMedia();
        pageMedia.setMediaAsset(media);
        CapturingScheduler scheduler = new CapturingScheduler();
        AtomicReference<DocumentPage> saved = new AtomicReference<>();

        DocumentProcessingRequestService service = service(
                page,
                pageMedia,
                Optional.empty(),
                true,
                scheduler,
                saved
        );

        var result = service.requestOcr(2L);

        assertEquals(JobType.OCR, result.jobType());
        assertSame(page, scheduler.page);
        assertSame(media, scheduler.media);
        assertEquals(ProcessingState.PENDING, saved.get().getProcessingState());
    }

    @Test
    void createsNewOcrJobWithPreferredInputAndMarksPagePending() {
        Document document = entity(new Document(), 1L);
        DocumentPage page = entity(new DocumentPage(), 2L);
        page.setDocument(document);
        MediaAsset media = entity(new MediaAsset(), 3L);
        DocumentPageMedia pageMedia = new DocumentPageMedia();
        pageMedia.setMediaAsset(media);
        CapturingScheduler scheduler = new CapturingScheduler();
        AtomicReference<DocumentPage> saved = new AtomicReference<>();

        var result = service(
                page,
                pageMedia,
                Optional.empty(),
                true,
                scheduler,
                saved
        ).createOcrJob(2L);

        assertEquals(JobType.OCR, result.jobType());
        assertTrue(scheduler.createdNew);
        assertSame(page, scheduler.page);
        assertSame(media, scheduler.media);
        assertEquals(ProcessingState.PENDING, saved.get().getProcessingState());
    }

    @Test
    void qualityAssessmentRequiresCurrentOcrResult() {
        DocumentPage page = entity(new DocumentPage(), 2L);

        DocumentProcessingRequestService service = service(
                page,
                new DocumentPageMedia(),
                Optional.empty(),
                true,
                new CapturingScheduler(),
                new AtomicReference<>()
        );

        assertThrows(
                InvalidDocumentProcessingRequestException.class,
                () -> service.requestQualityAssessment(2L)
        );
    }

    @Test
    void visionRequestLocksPageAndQueuesFreshManualAttempt() {
        Document document = entity(new Document(), 1L);
        DocumentPage page = entity(new DocumentPage(), 2L);
        page.setDocument(document);
        MediaAsset media = entity(new MediaAsset(), 3L);
        DocumentPageMedia pageMedia = entity(new DocumentPageMedia(), 4L);
        pageMedia.setDocumentPage(page);
        pageMedia.setMediaAsset(media);
        DocumentPageOcrResult ocrResult = entity(new DocumentPageOcrResult(), 5L);
        ocrResult.setDocumentPage(page);
        ocrResult.setDocumentPageMedia(pageMedia);
        ocrResult.setCurrent(true);
        DocumentPageQualityAssessment assessment = entity(
                new DocumentPageQualityAssessment(),
                6L
        );
        assessment.setDocumentPage(page);
        assessment.setDocumentPageMedia(pageMedia);
        assessment.setDocumentPageOcrResult(ocrResult);
        assessment.setCurrent(true);
        DocumentProcessingJob queued = entity(new DocumentProcessingJob(), 7L);
        queued.setJobType(JobType.VISION_OCR_ASSESSMENT);
        queued.setDocument(document);
        queued.setDocumentPage(page);

        DocumentRepository documents = mock(DocumentRepository.class);
        DocumentPageRepository pages = mock(DocumentPageRepository.class);
        DocumentPageMediaRepository pageMediaRepository = mock(
                DocumentPageMediaRepository.class
        );
        DocumentPageOcrResultRepository ocrResults = mock(
                DocumentPageOcrResultRepository.class
        );
        DocumentPageQualityAssessmentRepository assessments = mock(
                DocumentPageQualityAssessmentRepository.class
        );
        DocumentProcessingJobScheduler scheduler = mock(
                DocumentProcessingJobScheduler.class
        );
        when(pages.findByIdForUpdate(2L)).thenReturn(Optional.of(page));
        when(ocrResults.findByDocumentPage_IdAndCurrentTrue(2L))
                .thenReturn(Optional.of(ocrResult));
        when(assessments.findByDocumentPage_IdAndDocumentPageMedia_IdAndAssessmentTypeAndCurrentTrue(
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq(4L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(Optional.of(assessment));
        when(scheduler.queueVisionOcrAssessment(page, media, ocrResult, assessment))
                .thenReturn(queued);

        DocumentProcessingRequestService service = new DocumentProcessingRequestService(
                documents,
                pages,
                pageMediaRepository,
                ocrResults,
                assessments,
                scheduler,
                mock(DocumentChunkGenerationEligibilityService.class),
                mock(DocumentProcessingStateReconciler.class),
                new DocumentHistoryMapper(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        );

        var result = service.requestVisionAssessment(2L);

        assertEquals(7L, result.id());
        verify(pages).findByIdForUpdate(2L);
        verify(scheduler).queueVisionOcrAssessment(
                page,
                media,
                ocrResult,
                assessment
        );
    }

    @Test
    void chunkGenerationRequiresApprovedPage() {
        Document document = entity(new Document(), 1L);
        DocumentProcessingRequestService service = service(
                entity(new DocumentPage(), 2L),
                new DocumentPageMedia(),
                Optional.empty(),
                false,
                new CapturingScheduler(),
                new AtomicReference<>(),
                document
        );

        assertThrows(
                InvalidDocumentProcessingRequestException.class,
                () -> service.requestChunkGeneration(1L)
        );
    }

    private DocumentProcessingRequestService service(
            DocumentPage page,
            DocumentPageMedia media,
            Optional<DocumentPageOcrResult> currentOcr,
            boolean approvedPages,
            CapturingScheduler scheduler,
            AtomicReference<DocumentPage> saved
    ) {
        return service(page, media, currentOcr, approvedPages, scheduler, saved, new Document());
    }

    private DocumentProcessingRequestService service(
            DocumentPage page,
            DocumentPageMedia media,
            Optional<DocumentPageOcrResult> currentOcr,
            boolean approvedPages,
            CapturingScheduler scheduler,
            AtomicReference<DocumentPage> saved,
            Document document
    ) {
        DocumentRepository documents = proxy(
                DocumentRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findById"))
                        return Optional.of(document);
                    throw new AssertionError("Unexpected document call: " + method.getName());
                }
        );
        DocumentPageRepository pages = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findById" -> Optional.of(page);
                    case "save", "saveAndFlush" -> {
                        saved.set((DocumentPage) arguments[0]);
                        yield arguments[0];
                    }
                    case "existsByDocument_IdAndTranscriptionApprovalState" -> approvedPages;
                    default -> throw new AssertionError("Unexpected page call: " + method.getName());
                }
        );
        DocumentPageMediaRepository pageMedia = proxy(
                DocumentPageMediaRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findByDocumentPage_IdAndPreferredOcrInputTrue"))
                        return Optional.of(media);
                    throw new AssertionError("Unexpected page-media call: " + method.getName());
                }
        );
        DocumentPageOcrResultRepository ocrResults = proxy(
                DocumentPageOcrResultRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findByDocumentPage_IdAndCurrentTrue"))
                        return currentOcr;
                    throw new AssertionError("Unexpected OCR-result call: " + method.getName());
                }
        );
        DocumentChunkGenerationEligibilityService eligibility =
                org.mockito.Mockito.mock(
                        DocumentChunkGenerationEligibilityService.class
                );
        org.mockito.Mockito.when(eligibility.requireInput(
                org.mockito.ArgumentMatchers.anyLong()
        )).thenReturn(new ChunkGenerationInput(
                document,
                "0".repeat(64),
                approvedPages
                        ? List.of(new EligibleChunkPage(page, "text", "hash"))
                        : List.of(),
                List.of()
        ));

        return new DocumentProcessingRequestService(
                documents,
                pages,
                pageMedia,
                ocrResults,
                org.mockito.Mockito.mock(
                        fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualityAssessmentRepository.class
                ),
                scheduler,
                eligibility,
                org.mockito.Mockito.mock(DocumentProcessingStateReconciler.class),
                new DocumentHistoryMapper(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        );
    }

    private <T extends AppendOnlyEntity> T entity(T entity, Long id) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }

    private static final class CapturingScheduler
            extends DocumentProcessingJobScheduler {

        private DocumentPage page;
        private MediaAsset media;
        private boolean createdNew;

        private CapturingScheduler() {
            super(
                    rejecting(DocumentProcessingJobRepository.class),
                    new DocumentProcessingJobKeyFactory(),
                    fmi.ethnowear.support.ManagementEventTestSupport.events()
            );
        }

        @Override
        public DocumentProcessingJob queueOcr(
                DocumentPage page,
                MediaAsset inputMedia
        ) {
            this.page = page;
            this.media = inputMedia;
            return job(JobType.OCR, page.getDocument(), page);
        }

        @Override
        public DocumentProcessingJob createNewOcrJob(
                DocumentPage page,
                MediaAsset inputMedia
        ) {
            this.page = page;
            this.media = inputMedia;
            this.createdNew = true;
            return job(JobType.OCR, page.getDocument(), page);
        }

        @Override
        public DocumentProcessingJob queueOcrQualityAssessment(
                DocumentPage page,
                MediaAsset inputMedia,
                DocumentPageOcrResult ocrResult
        ) {
            return job(JobType.OCR_QUALITY_ASSESSMENT, page.getDocument(), page);
        }

        @Override
        public DocumentProcessingJob queueChunkGeneration(
                Document document,
                String generationInputHash
        ) {
            return job(JobType.CHUNK_GENERATION, document, null);
        }

        private DocumentProcessingJob job(
                JobType type,
                Document document,
                DocumentPage page
        ) {
            DocumentProcessingJob job = entity(new DocumentProcessingJob(), 9L);
            job.setJobType(type);
            job.setDocument(document);
            job.setDocumentPage(page);
            return job;
        }

        private <T extends AppendOnlyEntity> T entity(T entity, Long id) {
            EntityTestUtils.setId(entity, id);
            return entity;
        }
    }
}
