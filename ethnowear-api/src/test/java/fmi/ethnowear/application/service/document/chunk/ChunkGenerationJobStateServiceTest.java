package fmi.ethnowear.application.service.document.chunk;

import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobKeyFactory;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.service.worker.security.WorkerClaimTokenService;
import fmi.ethnowear.config.DocumentChunkingProperties;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkGenerationJobStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");

    @Test
    void claimsDueJobWithFutureLease() {
        Fixture fixture = fixture(JobStatus.QUEUED);
        when(fixture.repository().findDueJobsForUpdate(
                eq(JobType.CHUNK_GENERATION),
                anyCollection(),
                any(),
                any(Pageable.class)
        )).thenReturn(List.of(fixture.job()));

        var claimed = fixture.service().claimNextDue();

        assertEquals(71L, claimed.jobId());
        assertEquals(11L, claimed.documentId());
        assertEquals("a".repeat(64), claimed.generationInputHash());
        assertEquals(JobStatus.RUNNING, fixture.job().getStatus());
        assertEquals(1, fixture.job().getAttemptCount());
        assertTrue(fixture.job().getClaimExpiresAt()
                .isAfter(fixture.job().getClaimedAt()));
    }

    @Test
    void failureRetriesSafelyAndFinalAttemptBecomesDead() {
        Fixture fixture = fixture(JobStatus.RUNNING);
        fixture.job().setAttemptCount(1);
        when(fixture.repository().findByIdForUpdate(71L))
                .thenReturn(Optional.of(fixture.job()));

        fixture.service().fail(71L, new IllegalStateException("failure"));

        assertEquals(JobStatus.RETRY_WAIT, fixture.job().getStatus());
        assertEquals("CHUNK_GENERATION_FAILED", fixture.job().getErrorCode());
        assertNotNull(fixture.job().getActiveJobKey());

        fixture.job().setStatus(JobStatus.RUNNING);
        fixture.job().setAttemptCount(fixture.job().getMaxAttempts());
        fixture.service().fail(71L, new IllegalStateException("failure"));

        assertEquals(JobStatus.DEAD, fixture.job().getStatus());
        assertNull(fixture.job().getActiveJobKey());
    }

    @Test
    void cancellationRequestFinishesAsCancelled() {
        Fixture fixture = fixture(JobStatus.CANCEL_REQUESTED);
        fixture.job().setAttemptCount(1);
        when(fixture.repository().findByIdForUpdate(71L))
                .thenReturn(Optional.of(fixture.job()));

        fixture.service().fail(71L, new IllegalStateException("stopped"));

        assertEquals(JobStatus.CANCELLED, fixture.job().getStatus());
        assertNull(fixture.job().getActiveJobKey());
        assertNotNull(fixture.job().getFinishedAt());
    }

    @Test
    void heartbeatRenewsRunningLeaseAndObservesCancellation() {
        Fixture fixture = fixture(JobStatus.RUNNING);
        when(fixture.repository().findByIdForUpdate(71L))
                .thenReturn(Optional.of(fixture.job()));

        assertTrue(fixture.service().heartbeat(71L));
        assertEquals(
                NOW.plusSeconds(600),
                fixture.job().getClaimExpiresAt().toInstant(ZoneOffset.UTC)
        );

        fixture.job().setStatus(JobStatus.CANCEL_REQUESTED);
        assertFalse(fixture.service().heartbeat(71L));
    }

    @Test
    void obsoleteQueuedContractIsRetiredSafely() {
        Fixture fixture = fixture(JobStatus.QUEUED);
        ReflectionTestUtils.setField(
                fixture.job(),
                "jobKey",
                "CHUNK_GENERATION:DOCUMENT:11"
        );
        when(fixture.repository().findDueJobsForUpdate(
                eq(JobType.CHUNK_GENERATION),
                anyCollection(),
                any(),
                any(Pageable.class)
        )).thenReturn(List.of(fixture.job()));

        assertNull(fixture.service().claimNextDue());
        assertEquals(JobStatus.DEAD, fixture.job().getStatus());
        assertEquals(
                "CHUNK_GENERATION_CONTRACT_OBSOLETE",
                fixture.job().getErrorCode()
        );
        assertNull(fixture.job().getActiveJobKey());
    }

    private Fixture fixture(JobStatus status) {
        Document document = new Document();
        EntityTestUtils.setId(document, 11L);
        DocumentProcessingJob job = new DocumentProcessingJob();
        EntityTestUtils.setId(job, 71L);
        job.setDocument(document);
        job.setJobType(JobType.CHUNK_GENERATION);
        job.setStatus(status);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);
        job.assignJobKey(
                "CHUNK_GENERATION:DOCUMENT:11:INPUT:" + "a".repeat(64)
        );
        job.assignActiveJobKey("CHUNK_GENERATION:DOCUMENT:11");
        DocumentProcessingJobRepository repository = mock(
                DocumentProcessingJobRepository.class
        );
        when(repository.saveAndFlush(any())).thenAnswer(invocation ->
                invocation.getArgument(0));
        DocumentChunkingProperties properties = new DocumentChunkingProperties();

        ChunkGenerationJobStateService service = new ChunkGenerationJobStateService(
                repository,
                new DocumentProcessingJobKeyFactory(),
                new WorkerClaimTokenService(),
                properties,
                mock(ManagementEventPublisher.class),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        return new Fixture(service, repository, job);
    }

    private record Fixture(
            ChunkGenerationJobStateService service,
            DocumentProcessingJobRepository repository,
            DocumentProcessingJob job
    ) {
    }
}
