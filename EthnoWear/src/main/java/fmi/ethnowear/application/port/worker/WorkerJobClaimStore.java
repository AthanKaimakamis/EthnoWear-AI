package fmi.ethnowear.application.port.worker;

import fmi.ethnowear.application.model.worker.ClaimedWorkerJob;
import fmi.ethnowear.domain.model.document.processing.JobType;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

public interface WorkerJobClaimStore {

    Optional<ClaimedWorkerJob> claimNext(
            Set<JobType> supportedJobTypes,
            String workerId,
            LocalDateTime claimedAt,
            LocalDateTime leaseExpiresAt,
            LocalDateTime timeoutAt,
            String claimTokenHash
    );
}