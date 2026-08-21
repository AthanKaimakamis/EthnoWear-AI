package fmi.ethnowear.application.service.worker.security;

import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.exception.WorkerClaimExpiredException;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkerClaimValidatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 21, 8, 0);
    private static final String WORKER_ID = "page-extractor-1";

    private final WorkerClaimValidator validator = new WorkerClaimValidator();

    @Test
    void acceptsOnlyCurrentJobAttemptCredentials() {
        DocumentProcessingJob job = activeJob("current-token");

        assertDoesNotThrow(() -> validator.validate(
                job,
                new WorkerClaimCredentials(WORKER_ID, "current-token"),
                NOW
        ));

        assertThrows(WorkerClaimConflictException.class, () -> validator.validate(
                job,
                new WorkerClaimCredentials(WORKER_ID, "token-for-another-job"),
                NOW
        ));

        assertThrows(WorkerClaimConflictException.class, () -> validator.validate(
                job,
                new WorkerClaimCredentials(WORKER_ID, "token-from-previous-attempt"),
                NOW
        ));
    }

    @Test
    void rejectsDifferentWorkerTerminalStateAndExpiredLease() {
        DocumentProcessingJob job = activeJob("current-token");

        assertThrows(WorkerClaimConflictException.class, () -> validator.validate(
                job,
                new WorkerClaimCredentials("another-worker", "current-token"),
                NOW
        ));

        job.setStatus(JobStatus.SUCCEEDED);
        assertThrows(WorkerClaimConflictException.class, () -> validator.validate(
                job,
                new WorkerClaimCredentials(WORKER_ID, "current-token"),
                NOW
        ));

        job.setStatus(JobStatus.RUNNING);
        job.setClaimExpiresAt(NOW);
        assertThrows(WorkerClaimExpiredException.class, () -> validator.validate(
                job,
                new WorkerClaimCredentials(WORKER_ID, "current-token"),
                NOW
        ));
    }

    private DocumentProcessingJob activeJob(String token) {
        DocumentProcessingJob job = new DocumentProcessingJob();
        job.setStatus(JobStatus.RUNNING);
        job.setClaimedBy(WORKER_ID);
        job.assignClaimTokenHash(ContentHashUtils.sha256(token));
        job.setClaimExpiresAt(NOW.plusMinutes(2));
        return job;
    }
}
