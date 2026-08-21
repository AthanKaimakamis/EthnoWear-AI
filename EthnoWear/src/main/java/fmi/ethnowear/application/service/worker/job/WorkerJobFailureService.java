package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.failure.*;
import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.domain.model.document.processing.*;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkerJobFailureService {

    private static final Set<JobStatus> FAILURE_RESULTS = Set.of(
            JobStatus.RETRY_WAIT,
            JobStatus.FAILED,
            JobStatus.DEAD
    );

    private static final Set<String> FORBIDDEN_MESSAGE_PARTS = Set.of(
            "\n",
            "\r",
            "/",
            "\\",
            "file:",
            "password",
            "authorization",
            "bearer ",
            "token=",
            "secret=",
            "jdbc:",
            " at java.",
            " at org."
    );

    private final WorkerClaimedJobLoader jobLoader;
    private final DocumentProcessingJobRepository jobRepository;
    private final WorkerRetryPolicy retryPolicy;
    private final Clock clock;

    @Transactional
    public WorkerJobFailureDetails fail(
            Long jobId,
            WorkerClaimCredentials credentials,
            WorkerJobFailureCommand command
    ) {
        validate(command);

        DocumentProcessingJob job = jobLoader.loadForUpdate(jobId);

        if (FAILURE_RESULTS.contains(job.getStatus()))
            return existingResult(job, command);

        jobLoader.validateActive(job, credentials);

        if (job.getStatus() == JobStatus.CANCEL_REQUESTED)
            throw new WorkerClaimConflictException();

        LocalDateTime now = LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC
        );

        job.setErrorCode(command.errorCode());
        job.setSafeErrorMessage(command.safeErrorMessage().trim());
        job.setErrorDetailsJson(null);
        job.clearClaimOwnership();

        if (command.retryable() && job.getAttemptCount() < job.getMaxAttempts()) {
            job.setStatus(JobStatus.RETRY_WAIT);
            job.setAvailableAt(now.plus(retryPolicy.delay(job.getAttemptCount())));
            job.setFinishedAt(null);
        } else {
            job.setStatus(
                    job.getAttemptCount() >= job.getMaxAttempts()
                            ? JobStatus.DEAD
                            : JobStatus.FAILED
            );
            job.setFinishedAt(now);
            job.clearActiveJobKey();
        }

        jobRepository.saveAndFlush(job);
        return details(job, false);
    }

    private void validate(WorkerJobFailureCommand command) {
        if (command == null)
            throw new IllegalArgumentException("Failure report is required");

        String message = command.safeErrorMessage();

        if (message == null || message.isBlank())
            throw new IllegalArgumentException("Safe error message is required");

        String normalized = message.toLowerCase(Locale.ROOT);

        if (FORBIDDEN_MESSAGE_PARTS.stream().anyMatch(normalized::contains))
            throw new IllegalArgumentException("Safe error message contains restricted diagnostic data");
    }

    private @NonNull WorkerJobFailureDetails existingResult(
            @NonNull DocumentProcessingJob job,
            @NonNull WorkerJobFailureCommand command
    ) {
        if (!Objects.equals(job.getErrorCode(), command.errorCode())
                || !Objects.equals(
                job.getSafeErrorMessage(),
                command.safeErrorMessage().trim()
        ))
            throw new WorkerClaimConflictException();

        return details(job, true);
    }

    private @NonNull WorkerJobFailureDetails details(
            @NonNull DocumentProcessingJob job,
            boolean existing
    ) {
        Instant availableAt = job.getStatus() == JobStatus.RETRY_WAIT
                ? job.getAvailableAt().toInstant(ZoneOffset.UTC)
                : null;

        return new WorkerJobFailureDetails(
                job.getId(),
                job.getStatus(),
                availableAt,
                existing
        );
    }
}