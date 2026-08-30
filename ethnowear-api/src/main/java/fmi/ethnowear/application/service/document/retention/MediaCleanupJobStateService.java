package fmi.ethnowear.application.service.document.retention;

import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.service.worker.security.WorkerClaimTokenService;
import fmi.ethnowear.config.MediaRetentionProperties;
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
public class MediaCleanupJobStateService {

    private static final String PROCESSOR = "spring-media-cleanup";
    private static final Set<JobStatus> CLAIMABLE = Set.of(
            JobStatus.QUEUED,
            JobStatus.RETRY_WAIT
    );

    private final DocumentProcessingJobRepository repository;
    private final WorkerClaimTokenService tokenService;
    private final MediaRetentionProperties properties;
    private final ManagementEventPublisher managementEvents;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long claimNextDue() {
        LocalDateTime now = now();
        var jobs = repository.findDueJobsForUpdate(
                JobType.MEDIA_CLEANUP,
                CLAIMABLE,
                now,
                PageRequest.of(0, 1)
        );

        if (jobs.isEmpty())
            return null;

        DocumentProcessingJob job = jobs.getFirst();
        var token = tokenService.generate();

        job.setAttemptCount(job.getAttemptCount() + 1);
        job.setStatus(JobStatus.CLAIMED);
        job.setClaimedBy(PROCESSOR);
        job.setClaimedAt(now);
        job.setClaimExpiresAt(now.plus(properties.getMaximumExecutionTime()));
        job.assignClaimTokenHash(token.hash());
        job.setProcessorName(PROCESSOR);
        job.setProcessorVersion("1");
        job.setToolName("filesystem-retention");
        job.setToolVersion("1");
        repository.saveAndFlush(job);

        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(now);
        repository.saveAndFlush(job);
        managementEvents.processingJob(
                job,
                ManagementEvent.Action.STATUS_CHANGED
        );
        return job.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long jobId, int purgedMediaCount) {
        DocumentProcessingJob job = requireJob(jobId);
        LocalDateTime now = now();

        if (job.getStatus() == JobStatus.CANCEL_REQUESTED) {
            job.setStatus(JobStatus.CANCELLED);
            job.setCancellationReason("Media cleanup was cancelled");
        } else {
            job.setStatus(JobStatus.SUCCEEDED);
            job.setParametersJson(
                    "{\"policy\":\"KEEP_ORIGINAL_ONLY\","
                            + "\"purgedMediaCount\":"
                            + purgedMediaCount
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

        job.setErrorCode("MEDIA_CLEANUP_FAILED");
        job.setSafeErrorMessage(safeMessage(failure));
        job.clearClaimOwnership();

        if (job.getAttemptCount() < job.getMaxAttempts()) {
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
                        "Media-cleanup job no longer exists: " + jobId
                ));
    }

    private String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();

        if (message == null || message.isBlank())
            return "Generated media cleanup failed";

        String normalized = message.trim();
        return normalized.length() <= 1000
                ? normalized
                : normalized.substring(0, 1000);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
