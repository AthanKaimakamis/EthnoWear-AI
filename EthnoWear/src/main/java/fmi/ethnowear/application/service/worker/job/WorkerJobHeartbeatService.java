package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.heartbeat.*;
import fmi.ethnowear.application.exception.WorkerClaimExpiredException;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.config.WorkerApiProperties;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class WorkerJobHeartbeatService {

    private final WorkerApiProperties properties;
    private final WorkerClaimedJobLoader jobLoader;
    private final Clock clock;

    @Transactional
    public WorkerHeartbeatDetails heartbeat(
            Long jobId,
            WorkerClaimCredentials credentials,
            WorkerHeartbeatCommand command
    ) {
        if(jobId == null)
            throw new IllegalArgumentException("Job id is required");

        DocumentProcessingJob job = jobLoader.requireActive(jobId, credentials);

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        Duration lease = resolveLease(command);
        LocalDateTime leaseExpiresAt = now.plus(lease);

        if(job.getTimeoutAt() != null && leaseExpiresAt.isAfter(job.getTimeoutAt()))
            leaseExpiresAt = job.getTimeoutAt();

        if(!leaseExpiresAt.isAfter(now))
            throw new WorkerClaimExpiredException();

        job.setClaimExpiresAt(leaseExpiresAt);

        return new WorkerHeartbeatDetails(
                leaseExpiresAt.toInstant(ZoneOffset.UTC),
                job.getStatus() == JobStatus.CANCEL_REQUESTED
        );
    }

    private @NonNull Duration resolveLease(WorkerHeartbeatCommand command) {
        Integer leaseSeconds = command == null ? null : command.leaseSeconds();

        Duration lease = leaseSeconds == null
                ? properties.defaultLease()
                : Duration.ofSeconds(leaseSeconds);

        if(lease.compareTo(properties.minimumLease()) < 0)
            throw new IllegalArgumentException("Lease is shorter than the minimum allowed lease");

        if(lease.compareTo(properties.maximumLease()) > 0)
            throw new IllegalArgumentException("Lease exceeds the maximum allowed lease");

        return lease;
    }
}
