package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.completion.WorkerJobCompletionDetails;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobKeyFactory;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.WorkerTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerOcrCompletionServiceTest {

    @Test
    void promotesAcceptedResultAndQueuesQualityAssessmentIdempotently() {
        Document document = document(7L);
        DocumentPage page = page(document);
        MediaAsset input = image(31L);
        DocumentPageMedia pageMedia = pageMedia(page, input);
        DocumentProcessingJob job = ocrJob(document, page, input);
        DocumentPageOcrResult result = result(job, page, pageMedia, 51L, false);
        DocumentPageOcrResult previous = result(null, page, pageMedia, 50L, true);
        AtomicInteger queued = new AtomicInteger();

        WorkerJobCompletionService service = service(
                job,
                page,
                result,
                previous,
                queued
        );

        WorkerJobCompletionDetails completed = service.complete(
                11L,
                credentials()
        );
        WorkerJobCompletionDetails repeated = service.complete(
                11L,
                credentials()
        );

        assertEquals(JobStatus.SUCCEEDED, job.getStatus());
        assertTrue(result.isCurrent());
        assertFalse(previous.isCurrent());
        assertEquals("Разпознат текст", page.getRawOcrText());
        assertEquals(ProcessingState.COMPLETED, page.getProcessingState());
        assertEquals(ReviewState.REVIEW_REQUIRED, page.getReviewState());
        assertEquals(IndexingState.NOT_ELIGIBLE, page.getIndexingState());
        assertEquals(1, completed.queuedQualityAssessmentJobs());
        assertEquals(1, queued.get());
        assertTrue(repeated.existing());
    }

    private WorkerJobCompletionService service(
            DocumentProcessingJob job,
            DocumentPage page,
            DocumentPageOcrResult result,
            DocumentPageOcrResult previous,
            AtomicInteger queued
    ) {
        DocumentProcessingJobRepository jobs = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByIdForUpdate" -> Optional.of(job);
                    case "findByActiveJobKey" -> Optional.empty();
                    case "saveAndFlush" -> arguments[0];
                    default -> throw new AssertionError(
                            "Unexpected job repository call: " + method.getName()
                    );
                }
        );
        DocumentPageRepository pages = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("save") || method.getName().equals("saveAndFlush"))
                        return arguments[0];

                    throw new AssertionError(
                            "Unexpected page repository call: " + method.getName()
                    );
                }
        );
        DocumentPageOcrResultRepository results = proxy(
                DocumentPageOcrResultRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByProcessingJob_Id" -> Optional.of(result);
                    case "findByDocumentPage_IdAndCurrentTrue" ->
                            previous.isCurrent() ? Optional.of(previous) : Optional.empty();
                    case "saveAndFlush" -> arguments[0];
                    default -> throw new AssertionError(
                            "Unexpected OCR repository call: " + method.getName()
                    );
                }
        );
        DocumentProcessingJobScheduler scheduler =
                new DocumentProcessingJobScheduler(null, null, null) {
                    @Override
                    public DocumentProcessingJob queueOcrQualityAssessment(
                            DocumentPage queuedPage,
                            MediaAsset queuedInput,
                            DocumentPageOcrResult queuedResult
                    ) {
                        assertSame(page, queuedPage);
                        assertSame(job.getInputMediaAsset(), queuedInput);
                        assertSame(result, queuedResult);
                        queued.incrementAndGet();
                        return new DocumentProcessingJob();
                    }
                };

        return new WorkerJobCompletionService(
                new fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader(
                        jobs,
                        new fmi.ethnowear.application.service.worker.security.WorkerClaimValidator(),
                        Clock.fixed(NOW, ZoneOffset.UTC)
                ),
                jobs,
                pages,
                null,
                results,
                null,
                null,
                null,
                null,
                null,
                scheduler,
                org.mockito.Mockito.mock(
                        fmi.ethnowear.application.service.document.processing.DocumentProcessingStateReconciler.class
                ),
                new DocumentProcessingJobKeyFactory(),
                org.mockito.Mockito.mock(WorkerIndexingService.class),
                Clock.fixed(NOW, ZoneOffset.UTC),
                fmi.ethnowear.support.ManagementEventTestSupport.events(),
                org.mockito.Mockito.mock(fmi.ethnowear.application.service.document.figure.DocumentPageFigureLifecycleService.class),
                org.mockito.Mockito.mock(fmi.ethnowear.application.service.document.figure.FigureExtractionSchedulingService.class)
        );
    }

    private DocumentPage page(Document document) {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 21L);
        page.setDocument(document);
        return page;
    }

    private MediaAsset image(long id) {
        MediaAsset input = new MediaAsset();
        EntityTestUtils.setId(input, id);
        input.setMediaType(MediaType.IMAGE);
        input.setMimeType("image/png");
        input.setSizeBytes(1024L);
        return input;
    }

    private DocumentPageMedia pageMedia(DocumentPage page, MediaAsset input) {
        DocumentPageMedia media = new DocumentPageMedia();
        EntityTestUtils.setId(media, 41L);
        media.setDocumentPage(page);
        media.setMediaAsset(input);
        media.setPreferredOcrInput(true);
        return media;
    }

    private DocumentProcessingJob ocrJob(
            Document document,
            DocumentPage page,
            MediaAsset input
    ) {
        DocumentProcessingJob job = activePageExtractionJob(11L, document);
        job.setJobType(JobType.OCR);
        job.setDocumentPage(page);
        job.setInputMediaAsset(input);
        job.assignActiveJobKey("OCR:PAGE:21");
        return job;
    }

    private DocumentPageOcrResult result(
            DocumentProcessingJob job,
            DocumentPage page,
            DocumentPageMedia media,
            long id,
            boolean current
    ) {
        DocumentPageOcrResult result = new DocumentPageOcrResult();
        EntityTestUtils.setId(result, id);
        result.setDocumentPage(page);
        result.setDocumentPageMedia(media);
        result.setProcessingJob(job);
        result.setRawText("Разпознат текст");
        result.setOcrEngine("tesseract");
        result.setOcrEngineVersion("5.5");
        result.setOcrLanguage("bul");
        result.setOcrConfidence(new BigDecimal("0.9123"));
        result.setCurrent(current);
        return result;
    }
}
