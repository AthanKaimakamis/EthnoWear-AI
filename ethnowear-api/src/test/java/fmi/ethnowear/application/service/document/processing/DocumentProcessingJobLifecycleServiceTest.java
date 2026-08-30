package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.dto.document.command.processing.DocumentJobCancellationCommand;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.*;

class DocumentProcessingJobLifecycleServiceTest {

    @Test
    void retriesFailedJobAndRestoresItsActiveKey() {
        DocumentProcessingJob job = job(JobStatus.FAILED);
        job.setJobType(JobType.PAGE_EXTRACTION);
        job.setDocumentPage(null);
        job.setAttemptCount(1);
        job.setMaxAttempts(3);
        job.setClaimedBy("worker");
        job.setStartedAt(LocalDateTime.now());
        job.setFinishedAt(LocalDateTime.now());
        AtomicReference<DocumentProcessingJob> saved = new AtomicReference<>();

        var result = service(job, saved).retry(9L);

        assertEquals(JobStatus.RETRY_WAIT, result.status());
        assertEquals("PAGE_EXTRACTION:DOCUMENT:1", job.getActiveJobKey());
        assertNull(job.getClaimedBy());
        assertNull(job.getStartedAt());
        assertNull(job.getFinishedAt());
        assertNotNull(job.getAvailableAt());
        assertSame(job, saved.get());
    }

    @Test
    void resetsSameJobAtMaximumAttempts() {
        DocumentProcessingJob job = job(JobStatus.FAILED);
        job.setJobType(JobType.PAGE_EXTRACTION);
        job.setDocumentPage(null);
        job.setAttemptCount(3);
        job.setMaxAttempts(3);
        AtomicReference<DocumentProcessingJob> saved = new AtomicReference<>();

        var result = service(job, saved).retry(9L);

        assertSame(job, saved.get());
        assertEquals(9L, result.id());
        assertEquals(JobStatus.RETRY_WAIT, result.status());
        assertEquals(0, result.attemptCount());
        assertEquals("PAGE_EXTRACTION:DOCUMENT:1", saved.get().getActiveJobKey());
    }

    @Test
    void rejectsRetryingCompletedJob() {
        DocumentProcessingJob job = job(JobStatus.SUCCEEDED);
        job.setJobType(JobType.PAGE_EXTRACTION);
        job.setDocumentPage(null);
        job.setAttemptCount(1);
        job.setFinishedAt(LocalDateTime.now());
        assertThrows(
                fmi.ethnowear.application.exception.InvalidDocumentJobTransitionException.class,
                () -> service(job, new AtomicReference<>()).retry(9L)
        );
    }

    @Test
    void retriesFailedOcrUsingSameJobIdentity() {
        DocumentProcessingJob job = job(JobStatus.FAILED);
        job.assignJobKey("OCR:PAGE:2:JOB:replacement");
        AtomicReference<DocumentProcessingJob> saved = new AtomicReference<>();

        var result = service(job, saved).retry(9L);

        assertEquals(9L, result.id());
        assertSame(job, saved.get());
        assertEquals(JobStatus.RETRY_WAIT, saved.get().getStatus());
        assertEquals("OCR:PAGE:2", saved.get().getActiveJobKey());
    }

    @Test
    void immediatelyCancelsQueuedJobAndClearsActiveKey() {
        DocumentProcessingJob job = job(JobStatus.QUEUED);
        job.assignActiveJobKey("OCR:PAGE:2");

        var result = service(job, new AtomicReference<>()).cancel(
                9L,
                new DocumentJobCancellationCommand("No longer needed")
        );

        assertEquals(JobStatus.CANCELLED, result.status());
        assertNull(job.getActiveJobKey());
        assertNotNull(job.getFinishedAt());
        assertEquals("No longer needed", job.getCancellationReason());
    }

    @Test
    void requestsCancellationForRunningJobAndKeepsActiveKey() {
        DocumentProcessingJob job = job(JobStatus.RUNNING);
        job.assignActiveJobKey("OCR:PAGE:2");

        var result = service(job, new AtomicReference<>()).cancel(
                9L,
                new DocumentJobCancellationCommand("Stop processing")
        );

        assertEquals(JobStatus.CANCEL_REQUESTED, result.status());
        assertEquals("OCR:PAGE:2", job.getActiveJobKey());
        assertNull(job.getFinishedAt());
    }

    @Test
    void rejectsDuplicateIdsInBulkRetry() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service(job(JobStatus.FAILED), new AtomicReference<>())
                        .retryAll(List.of(9L, 9L))
        );

        assertEquals("Processing job ids must be unique", error.getMessage());
    }

    private DocumentProcessingJobLifecycleService service(
            DocumentProcessingJob job,
            AtomicReference<DocumentProcessingJob> saved
    ) {
        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByIdForUpdate" -> Optional.of(job);
                    case "findByActiveJobKey" -> Optional.empty();
                    case "saveAndFlush" -> {
                        saved.set((DocumentProcessingJob) arguments[0]);
                        yield arguments[0];
                    }
                    default -> throw new AssertionError("Unexpected job call: " + method.getName());
                }
        );

        return new DocumentProcessingJobLifecycleService(
                repository,
                new DocumentProcessingJobKeyFactory(),
                new DocumentHistoryMapper(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        );
    }

    private DocumentProcessingJob job(JobStatus status) {
        Document document = new Document();
        EntityTestUtils.setId(document, 1L);
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 2L);
        page.setDocument(document);
        DocumentProcessingJob job = new DocumentProcessingJob();
        EntityTestUtils.setId(job, 9L);
        job.setJobType(JobType.OCR);
        job.setStatus(status);
        job.setDocument(document);
        job.setDocumentPage(page);
        MediaAsset input = new MediaAsset();
        EntityTestUtils.setId(input, 3L);
        job.setInputMediaAsset(input);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);
        return job;
    }
}
