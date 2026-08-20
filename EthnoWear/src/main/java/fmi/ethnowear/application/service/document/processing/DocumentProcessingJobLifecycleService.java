package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.dto.document.command.processing.DocumentJobCancellationCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.exception.ActiveDocumentJobExistsException;
import fmi.ethnowear.application.exception.InvalidDocumentJobTransitionException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DocumentProcessingJobLifecycleService {

    private static final String ACTIVE_JOB_CONSTRAINT =
            "UQ_DocumentProcessingJobs_ActiveJobKey";

    private static final Set<JobStatus> RETRYABLE = Set.of(
            JobStatus.FAILED,
            JobStatus.TIMED_OUT
    );

    private static final Set<JobStatus> IMMEDIATELY_CANCELLABLE = Set.of(
            JobStatus.QUEUED,
            JobStatus.RETRY_WAIT
    );

    private static final Set<JobStatus> RUNNING = Set.of(
            JobStatus.CLAIMED,
            JobStatus.RUNNING
    );

    private final DocumentProcessingJobRepository jobRepository;
    private final DocumentProcessingJobKeyFactory keyFactory;
    private final DocumentHistoryMapper historyMapper;

    @Transactional
    public DocumentProcessingJobDetails retry(Long jobId) {
        DocumentProcessingJob job = requireJob(jobId);

        if (!RETRYABLE.contains(job.getStatus()))
            throw new InvalidDocumentJobTransitionException(
                    "Only failed or timed-out jobs can be retried"
            );

        if (job.getAttemptCount() >= job.getMaxAttempts())
            throw new InvalidDocumentJobTransitionException(
                    "The processing job has reached its maximum attempts"
            );

        String activeJobKey = activeJobKey(job);

        jobRepository.findByActiveJobKey(activeJobKey)
                .filter(active -> !active.getId().equals(job.getId()))
                .ifPresent(active -> {
                    throw new ActiveDocumentJobExistsException(job.getJobType());
                });

        job.setStatus(JobStatus.RETRY_WAIT);
        job.setAvailableAt(LocalDateTime.now(ZoneOffset.UTC));
        job.setClaimedBy(null);
        job.setClaimedAt(null);
        job.setClaimExpiresAt(null);
        job.setStartedAt(null);
        job.setFinishedAt(null);
        job.setCancellationReason(null);
        job.assignActiveJobKey(activeJobKey);

        DocumentProcessingJob savedJob;

        try {
            savedJob = jobRepository.saveAndFlush(job);
        } catch (DataIntegrityViolationException ex) {
            if (isActiveJobConflict(ex))
                throw new ActiveDocumentJobExistsException(job.getJobType());

            throw ex;
        }

        return historyMapper.toDetails(savedJob);
    }

    @Transactional
    public DocumentProcessingJobDetails cancel(
            Long jobId,
            DocumentJobCancellationCommand command
    ) {
        validateCancellation(command);

        DocumentProcessingJob job = requireJob(jobId);
        JobStatus status = job.getStatus();

        if (status == JobStatus.CANCEL_REQUESTED)
            return historyMapper.toDetails(job);

        if (IMMEDIATELY_CANCELLABLE.contains(status)) {
            job.setStatus(JobStatus.CANCELLED);
            job.setCancellationReason(command.reason().trim());
            job.setFinishedAt(LocalDateTime.now(ZoneOffset.UTC));
            job.clearActiveJobKey();
        } else if (RUNNING.contains(status)) {
            job.setStatus(JobStatus.CANCEL_REQUESTED);
            job.setCancellationReason(command.reason().trim());
        } else {
            throw new InvalidDocumentJobTransitionException(
                    "The processing job cannot be cancelled from state "
                            + status
            );
        }

        return historyMapper.toDetails(jobRepository.saveAndFlush(job));
    }

    @Contract("null -> fail")
    private @NonNull DocumentProcessingJob requireJob(Long jobId) {
        if (jobId == null)
            throw new IllegalArgumentException("Document processing job id is required");

        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Document processing job", jobId));
    }

    private String activeJobKey(@NonNull DocumentProcessingJob job) {
        if (job.getDocumentPage() != null)
            return keyFactory.forPage(
                    job.getJobType(),
                    job.getDocumentPage().getId()
            );

        if (job.getKnowledgeChunk() != null)
            return keyFactory.forChunk(
                    job.getJobType(),
                    job.getKnowledgeChunk().getId()
            );

        if (job.getDocument() != null)
            return keyFactory.forDocument(
                    job.getJobType(),
                    job.getDocument().getId()
            );

        throw new InvalidDocumentJobTransitionException("The processing job has no retry target");
    }

    private void validateCancellation(DocumentJobCancellationCommand command) {
        if (command == null || command.reason() == null || command.reason().isBlank())
            throw new IllegalArgumentException("Cancellation reason is required");

        if (command.reason().length() > 500)
            throw new IllegalArgumentException("Cancellation reason cannot exceed 500 characters");
    }

    private boolean isActiveJobConflict(Throwable error) {
        Throwable current = error;

        while (current != null) {
            String message = current.getMessage();

            if (message != null && message.contains(ACTIVE_JOB_CONSTRAINT))
                return true;

            current = current.getCause();
        }

        return false;
    }
}