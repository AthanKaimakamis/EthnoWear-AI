package fmi.ethnowear.application.service.worker.security;

import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.exception.WorkerClaimExpiredException;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.util.ContentHashUtils;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Set;

@Component
public class WorkerClaimValidator {

    private static final Set<JobStatus> CLAIMABLE_STATUSES = Set.of(
            JobStatus.RUNNING,
            JobStatus.CANCEL_REQUESTED
    );

    public void validate(
            DocumentProcessingJob job,
            WorkerClaimCredentials credentials,
            LocalDateTime now
    ) {
        if(job == null)
            throw new WorkerClaimConflictException();

        if(!CLAIMABLE_STATUSES.contains(job.getStatus()))
            throw new WorkerClaimConflictException();

        if(!secureEquals(job.getClaimedBy(), credentials.workerId()))
            throw new WorkerClaimConflictException();

        String suppliedHash = ContentHashUtils.sha256(credentials.claimToken());

        if(!secureEquals(job.getClaimTokenHash(), suppliedHash))
            throw new WorkerClaimConflictException();

        if(job.getClaimExpiresAt() == null || !job.getClaimExpiresAt().isAfter(now))
            throw new WorkerClaimExpiredException();
    }

    private boolean secureEquals(String expected, String actual) {
        if(expected == null || actual == null)
            return false;

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
