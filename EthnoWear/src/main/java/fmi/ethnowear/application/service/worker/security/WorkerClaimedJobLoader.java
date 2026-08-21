package fmi.ethnowear.application.service.worker.security;

import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class WorkerClaimedJobLoader {

    private final DocumentProcessingJobRepository jobRepository;
    private final WorkerClaimValidator claimValidator;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob requireActive(Long jobId, WorkerClaimCredentials credentials) {
        DocumentProcessingJob job = loadForUpdate(jobId);
        validateActive(job, credentials);
        return job;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentProcessingJob loadForUpdate(Long jobId) {
        if (jobId == null)
            throw new IllegalArgumentException("Job id is required");

        return jobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Document processing job", jobId));
    }

    public void validateActive(DocumentProcessingJob job, WorkerClaimCredentials credentials) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        claimValidator.validate(job, credentials, now);
    }
}
