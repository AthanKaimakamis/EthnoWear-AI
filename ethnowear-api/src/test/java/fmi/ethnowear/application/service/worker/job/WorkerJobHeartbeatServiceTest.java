package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.heartbeat.WorkerHeartbeatCommand;
import fmi.ethnowear.application.dto.worker.heartbeat.WorkerHeartbeatDetails;
import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.exception.WorkerClaimExpiredException;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.application.service.worker.security.WorkerClaimValidator;
import fmi.ethnowear.config.WorkerApiProperties;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.util.ContentHashUtils;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerJobHeartbeatServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
    private static final String WORKER_ID = "pdf-worker-01";
    private static final String CLAIM_TOKEN = "claim-token";

    @Test
    void renewsLeaseAndReportsCancellation() {
        DocumentProcessingJob job = activeJob();
        job.setStatus(JobStatus.CANCEL_REQUESTED);
        WorkerJobHeartbeatService service = service(job);

        WorkerHeartbeatDetails result = service.heartbeat(
                7L,
                credentials(),
                new WorkerHeartbeatCommand(90)
        );

        assertEquals(NOW.plusSeconds(90), result.leaseExpiresAt());
        assertTrue(result.cancellationRequested());
    }

    @Test
    void capsLeaseAtJobTimeout() {
        DocumentProcessingJob job = activeJob();
        job.setTimeoutAt(LocalDateTime.ofInstant(NOW.plusSeconds(45), ZoneOffset.UTC));

        WorkerHeartbeatDetails result = service(job).heartbeat(
                7L,
                credentials(),
                new WorkerHeartbeatCommand(120)
        );

        assertEquals(NOW.plusSeconds(45), result.leaseExpiresAt());
    }

    @Test
    void rejectsWrongClaimToken() {
        assertThrows(
                WorkerClaimConflictException.class,
                () -> service(activeJob()).heartbeat(
                        7L,
                        new WorkerClaimCredentials(WORKER_ID, "wrong-token"),
                        null
                )
        );
    }

    @Test
    void rejectsExpiredClaim() {
        DocumentProcessingJob job = activeJob();
        job.setClaimExpiresAt(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));

        assertThrows(
                WorkerClaimExpiredException.class,
                () -> service(job).heartbeat(7L, credentials(), null)
        );
    }

    private WorkerJobHeartbeatService service(DocumentProcessingJob job) {
        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findByIdForUpdate"))
                        return Optional.of(job);

                    throw new AssertionError("Unexpected repository call: " + method.getName());
                }
        );

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        WorkerClaimedJobLoader jobLoader = new WorkerClaimedJobLoader(
                repository,
                new WorkerClaimValidator(),
                clock
        );

        return new WorkerJobHeartbeatService(
                properties(),
                jobLoader,
                clock
        );
    }

    private DocumentProcessingJob activeJob() {
        DocumentProcessingJob job = new DocumentProcessingJob();
        job.setStatus(JobStatus.RUNNING);
        job.setClaimedBy(WORKER_ID);
        job.assignClaimTokenHash(ContentHashUtils.sha256(CLAIM_TOKEN));
        job.setClaimExpiresAt(LocalDateTime.ofInstant(NOW.plusSeconds(30), ZoneOffset.UTC));
        job.setTimeoutAt(LocalDateTime.ofInstant(NOW.plusSeconds(600), ZoneOffset.UTC));
        return job;
    }

    @Contract(" -> new")
    private @NonNull WorkerClaimCredentials credentials() {
        return new WorkerClaimCredentials(WORKER_ID, CLAIM_TOKEN);
    }

    @Contract(" -> new")
    private @NonNull WorkerApiProperties properties() {
        return new WorkerApiProperties(
                true,
                "01234567890123456789012345678901",
                Duration.ofSeconds(30),
                Duration.ofSeconds(60),
                Duration.ofSeconds(120),
                Duration.ofSeconds(20),
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                DataSize.ofMegabytes(200),
                DataSize.ofMegabytes(20),
                1000,
                300,
                10000,
                10000,
                50000000,
                2_000_000,
                DataSize.ofMegabytes(10)
        );
    }
}
