package fmi.ethnowear.application.service.document.chunk;

import fmi.ethnowear.application.model.document.chunk.ClaimedChunkGenerationJob;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobKeyFactory;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.service.worker.security.WorkerClaimTokenService;
import fmi.ethnowear.config.DocumentChunkingProperties;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChunkGenerationJobStateService {

    private static final String PROCESSOR = "spring-chunk-generation";
    private static final Set<JobStatus> CLAIMABLE = Set.of(
            JobStatus.QUEUED,
            JobStatus.RETRY_WAIT
    );

    private final DocumentProcessingJobRepository repository;
    private final DocumentProcessingJobKeyFactory keyFactory;
    private final WorkerClaimTokenService tokenService;
    private final DocumentChunkingProperties properties;
    private final ManagementEventPublisher managementEvents;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimedChunkGenerationJob claimNextDue() {
        LocalDateTime now = now();
        var jobs = repository.findDueJobsForUpdate(
                JobType.CHUNK_GENERATION,
                CLAIMABLE,
                now,
                PageRequest.of(0, 1)
        );

        if(jobs.isEmpty())
            return null;

        DocumentProcessingJob job = jobs.getFirst();
        if(job.getDocument() == null)
            throw new IllegalStateException(
                    "Chunk-generation job has no document target"
            );

        String generationInputHash;
        try {
            generationInputHash = keyFactory.chunkGenerationInputHash(
                    job.getJobKey()
            );
        } catch (IllegalArgumentException ex) {
            retireObsoleteJob(job, now);
            return null;
        }

        var token = tokenService.generate();
        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setStatus(JobStatus.CLAIMED);
        job.setClaimedBy(PROCESSOR);
        job.setClaimedAt(now);
        job.setClaimExpiresAt(
                now.plus(properties.getMaximumExecutionTime())
        );
        job.assignClaimTokenHash(token.hash());
        job.setProcessorName(PROCESSOR);
        job.setProcessorVersion(properties.getVersion());
        job.setToolName(properties.getStrategy());
        job.setToolVersion(properties.getVersion());
        repository.saveAndFlush(job);

        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(now);
        repository.saveAndFlush(job);
        managementEvents.processingJob(
                job,
                ManagementEvent.Action.STATUS_CHANGED
        );

        return new ClaimedChunkGenerationJob(
                job.getId(),
                job.getDocument().getId(),
                generationInputHash
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean heartbeat(Long jobId) {
        DocumentProcessingJob job = requireJob(jobId);

        if(job.getStatus() == JobStatus.CANCEL_REQUESTED)
            return false;

        if(job.getStatus() != JobStatus.RUNNING)
            return false;

        job.setClaimExpiresAt(
                now().plus(properties.getMaximumExecutionTime())
        );
        repository.saveAndFlush(job);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long jobId, int chunkCount) {
        DocumentProcessingJob job = requireJob(jobId);
        LocalDateTime now = now();

        if(job.getStatus() == JobStatus.CANCEL_REQUESTED) {
            job.setStatus(JobStatus.CANCELLED);
            if(job.getCancellationReason() == null)
                job.setCancellationReason(
                        "Chunk generation was cancelled"
                );
        } else {
            job.setStatus(JobStatus.SUCCEEDED);
            job.setParametersJson(
                    "{\"generationInputHash\":\""
                            + keyFactory.chunkGenerationInputHash(
                                    job.getJobKey()
                            )
                            + "\",\"chunkCount\":"
                            + chunkCount
                            + "}"
            );
        }

        job.setFinishedAt(now);
        job.clearActiveJobKey();
        job.clearClaimOwnership();
        repository.saveAndFlush(job);
        managementEvents.processingJob(
                job,
                ManagementEvent.Action.STATUS_CHANGED
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long jobId, RuntimeException failure) {
        DocumentProcessingJob job = requireJob(jobId);
        LocalDateTime now = now();

        if(job.getStatus() == JobStatus.CANCEL_REQUESTED) {
            job.setStatus(JobStatus.CANCELLED);
            job.setFinishedAt(now);
            job.clearActiveJobKey();
            job.clearClaimOwnership();
            repository.saveAndFlush(job);
            managementEvents.processingJob(
                    job,
                    ManagementEvent.Action.STATUS_CHANGED
            );
            return;
        }

        job.setErrorCode("CHUNK_GENERATION_FAILED");
        job.setSafeErrorMessage(safeMessage(failure));
        job.clearClaimOwnership();

        if(job.getAttemptCount() < job.getMaxAttempts()) {
            job.setStatus(JobStatus.RETRY_WAIT);
            job.setAvailableAt(now.plus(properties.getRetryDelay()));
        } else {
            job.setStatus(JobStatus.DEAD);
            job.setFinishedAt(now);
            job.clearActiveJobKey();
        }

        repository.saveAndFlush(job);
        managementEvents.processingJob(
                job,
                ManagementEvent.Action.STATUS_CHANGED
        );
    }

    private DocumentProcessingJob requireJob(Long jobId) {
        return repository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new IllegalStateException(
                        "Chunk-generation job no longer exists: " + jobId
                ));
    }

    private void retireObsoleteJob(
            DocumentProcessingJob job,
            LocalDateTime now
    ) {
        job.setStatus(JobStatus.DEAD);
        job.setErrorCode("CHUNK_GENERATION_CONTRACT_OBSOLETE");
        job.setSafeErrorMessage(
                "Create a new chunk-generation job for the current document text"
        );
        job.setFinishedAt(now);
        job.clearActiveJobKey();
        job.clearClaimOwnership();
        repository.saveAndFlush(job);
        managementEvents.processingJob(
                job,
                ManagementEvent.Action.STATUS_CHANGED
        );
    }

    private String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if(message == null || message.isBlank())
            return "Document chunk generation failed";

        String normalized = message.trim();
        return normalized.length() <= 1000
                ? normalized
                : normalized.substring(0, 1000);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
