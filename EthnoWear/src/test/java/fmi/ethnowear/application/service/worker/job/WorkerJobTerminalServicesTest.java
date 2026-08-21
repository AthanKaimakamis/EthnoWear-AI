package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.completion.WorkerJobCompletionDetails;
import fmi.ethnowear.application.dto.worker.failure.WorkerJobFailureCommand;
import fmi.ethnowear.application.dto.worker.failure.WorkerJobFailureDetails;
import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobKeyFactory;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.application.service.worker.security.WorkerClaimValidator;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.WorkerTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerJobTerminalServicesTest {

    @Test
    void completionQueuesOnlyMissingOcrJobsAndIsIdempotent() {
        Document document = document(7L);
        DocumentProcessingJob extraction = activePageExtractionJob(11L, document);
        extraction.assignActiveJobKey("PAGE_EXTRACTION:DOCUMENT:7");
        DocumentPage first = page(document, 21L, 0, 1);
        DocumentPage second = page(document, 22L, 1, 2);
        List<DocumentPage> pages = List.of(first, second);
        List<DocumentPageMedia> inputs = List.of(input(first, 31L), input(second, 32L));
        AtomicInteger queued = new AtomicInteger();

        DocumentProcessingJobRepository jobs = jobRepository(
                extraction,
                "OCR:PAGE:21"
        );
        WorkerJobCompletionService service = completionService(
                extraction,
                pages,
                inputs,
                jobs,
                queued
        );

        WorkerJobCompletionDetails completed = service.complete(11L, credentials());
        WorkerJobCompletionDetails repeated = service.complete(11L, credentials());

        assertEquals(1, completed.queuedOcrJobs());
        assertEquals(1, queued.get());
        assertEquals(JobStatus.SUCCEEDED, extraction.getStatus());
        assertEquals(ProcessingState.PROCESSING, document.getProcessingState());
        assertNull(extraction.getActiveJobKey());
        assertTrue(repeated.existing());
        assertEquals(1, queued.get());
    }

    @Test
    void failureRetryIsBoundedIdempotentAndRejectsDiagnosticLeakage() {
        DocumentProcessingJob job = activePageExtractionJob(11L, document(7L));
        job.assignActiveJobKey("PAGE_EXTRACTION:DOCUMENT:7");
        DocumentProcessingJobRepository jobs = jobRepository(job, null);
        WorkerJobFailureService service = failureService(job, jobs);
        WorkerJobFailureCommand command = new WorkerJobFailureCommand(
                "RENDER_FAILED",
                "Renderer failed safely",
                true
        );

        WorkerJobFailureDetails failed = service.fail(11L, credentials(), command);
        WorkerJobFailureDetails repeated = service.fail(11L, credentials(), command);

        assertEquals(JobStatus.RETRY_WAIT, failed.status());
        assertNotNull(failed.availableAt());
        assertTrue(repeated.existing());
        assertNull(job.getClaimTokenHash());
        assertNotNull(job.getActiveJobKey());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.fail(
                        11L,
                        credentials(),
                        new WorkerJobFailureCommand(
                                "RENDER_FAILED",
                                "Failed at /private/document.pdf",
                                false
                        )
                )
        );
    }

    @Test
    void staleWorkerCannotReportFailureAfterReassignment() {
        DocumentProcessingJob job = activePageExtractionJob(11L, document(7L));
        job.setClaimedBy("replacement-worker");
        job.clearClaimTokenHash();
        job.assignClaimTokenHash(fmi.ethnowear.util.ContentHashUtils.sha256("replacement-token"));
        WorkerJobFailureService service = failureService(job, jobRepository(job, null));

        assertThrows(
                WorkerClaimConflictException.class,
                () -> service.fail(
                        11L,
                        credentials(),
                        new WorkerJobFailureCommand(
                                "RENDER_FAILED",
                                "Renderer failed safely",
                                true
                        )
                )
        );
        assertEquals(JobStatus.RUNNING, job.getStatus());
        assertEquals("replacement-worker", job.getClaimedBy());
    }

    @Test
    void cancellationRequiresRequestAndIsIdempotent() {
        Document document = document(7L);
        DocumentProcessingJob job = activePageExtractionJob(11L, document);
        job.assignActiveJobKey("PAGE_EXTRACTION:DOCUMENT:7");
        DocumentProcessingJobRepository jobs = jobRepository(job, null);
        WorkerJobCancellationService service = new WorkerJobCancellationService(
                loader(job, jobs),
                jobs,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThrows(
                WorkerClaimConflictException.class,
                () -> service.acknowledge(11L, credentials())
        );

        job.setStatus(JobStatus.CANCEL_REQUESTED);
        assertFalse(service.acknowledge(11L, credentials()).existing());
        assertTrue(service.acknowledge(11L, credentials()).existing());
        assertEquals(JobStatus.CANCELLED, job.getStatus());
        assertEquals(ProcessingState.CANCELLED, document.getProcessingState());
        assertNull(job.getActiveJobKey());
        assertNull(job.getClaimTokenHash());
    }

    private WorkerJobCompletionService completionService(
            DocumentProcessingJob job,
            List<DocumentPage> pages,
            List<DocumentPageMedia> inputs,
            DocumentProcessingJobRepository jobs,
            AtomicInteger queued
    ) {
        DocumentPageRepository pageRepository = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> switch(method.getName()) {
                    case "findByDocument_IdOrderByPageSequenceAsc" -> pages;
                    case "countByDocument_Id" -> (long) pages.size();
                    case "saveAll" -> arguments[0];
                    default -> throw new AssertionError("Unexpected page repository call: " + method.getName());
                }
        );
        DocumentPageMediaRepository mediaRepository = proxy(
                DocumentPageMediaRepository.class,
                (ignored, method, arguments) -> switch(method.getName()) {
                    case "findPageIdsWithRendition" -> pages.stream().map(DocumentPage::getId).toList();
                    case "findByDocumentPage_IdInAndPreferredOcrInputTrue" -> inputs;
                    default -> throw new AssertionError("Unexpected media repository call: " + method.getName());
                }
        );
        DocumentRepository documents = proxy(
                DocumentRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("save"))
                        return arguments[0];

                    throw new AssertionError("Unexpected document repository call: " + method.getName());
                }
        );
        DocumentProcessingJobScheduler scheduler = new DocumentProcessingJobScheduler(null, null) {
            @Override
            public DocumentProcessingJob queueOcr(DocumentPage page, MediaAsset inputMedia) {
                queued.incrementAndGet();
                return new DocumentProcessingJob();
            }
        };

        return new WorkerJobCompletionService(
                loader(job, jobs),
                jobs,
                pageRepository,
                mediaRepository,
                documents,
                scheduler,
                new DocumentProcessingJobKeyFactory(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private WorkerJobFailureService failureService(
            DocumentProcessingJob job,
            DocumentProcessingJobRepository jobs
    ) {
        return new WorkerJobFailureService(
                loader(job, jobs),
                jobs,
                new WorkerRetryPolicy(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private WorkerClaimedJobLoader loader(
            DocumentProcessingJob job,
            DocumentProcessingJobRepository repository
    ) {
        return new WorkerClaimedJobLoader(
                repository,
                new WorkerClaimValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private DocumentProcessingJobRepository jobRepository(
            DocumentProcessingJob job,
            String existingActiveKey
    ) {
        return proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch(method.getName()) {
                    case "findByIdForUpdate" -> Optional.of(job);
                    case "findByActiveJobKey" -> existingActiveKey != null
                            && existingActiveKey.equals(arguments[0])
                            ? Optional.of(new DocumentProcessingJob())
                            : Optional.empty();
                    case "saveAndFlush" -> arguments[0];
                    default -> throw new AssertionError("Unexpected job repository call: " + method.getName());
                }
        );
    }

    private DocumentPage page(Document document, long id, int pdfIndex, int sequence) {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, id);
        page.setDocument(document);
        page.setPdfPageIndex(pdfIndex);
        page.setPageSequence(sequence);
        return page;
    }

    private DocumentPageMedia input(DocumentPage page, long mediaId) {
        MediaAsset asset = new MediaAsset();
        EntityTestUtils.setId(asset, mediaId);
        asset.setMediaType(MediaType.IMAGE);
        DocumentPageMedia media = new DocumentPageMedia();
        media.setDocumentPage(page);
        media.setMediaAsset(asset);
        media.setRenditionType(DocumentPageRenditionType.PDF_PAGE_RENDER);
        media.setPreferredOcrInput(true);
        return media;
    }
}
