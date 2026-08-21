package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.cancellation.WorkerJobCancellationDetails;
import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.domain.model.document.processing.*;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

@Service
@RequiredArgsConstructor
public class WorkerJobCancellationService {

    private final WorkerClaimedJobLoader jobLoader;
    private final DocumentProcessingJobRepository jobRepository;
    private final Clock clock;

    @Transactional
    public WorkerJobCancellationDetails acknowledge(Long jobId, WorkerClaimCredentials credentials) {
        DocumentProcessingJob job = jobLoader.loadForUpdate(jobId);

        if (job.getStatus() == JobStatus.CANCELLED)
            return details(job, true);

        if (job.getStatus() != JobStatus.CANCEL_REQUESTED)
            throw new WorkerClaimConflictException();

        jobLoader.validateActive(job, credentials);

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);

        job.setStatus(JobStatus.CANCELLED);
        job.setFinishedAt(now);
        job.clearClaimOwnership();
        job.clearActiveJobKey();

        if (job.getJobType() == JobType.PAGE_EXTRACTION && job.getDocument() != null)
            job.getDocument().setProcessingState(ProcessingState.CANCELLED);

        if (job.getDocumentPage() != null)
            job.getDocumentPage().setProcessingState(ProcessingState.CANCELLED);

        jobRepository.saveAndFlush(job);
        return details(job, false);
    }

    @Contract("_, _ -> new")
    private @NonNull WorkerJobCancellationDetails details(
            @NonNull DocumentProcessingJob job,
            boolean existing
    ) {
        return new WorkerJobCancellationDetails(
                job.getId(),
                job.getStatus(),
                job.getFinishedAt().toInstant(ZoneOffset.UTC),
                existing
        );
    }
}