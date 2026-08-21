package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.*;
import fmi.ethnowear.application.dto.worker.job.*;
import fmi.ethnowear.application.model.worker.ClaimedWorkerJob;
import fmi.ethnowear.application.model.worker.WorkerClaimToken;
import fmi.ethnowear.application.port.worker.WorkerJobClaimStore;
import fmi.ethnowear.application.service.worker.security.WorkerClaimTokenService;
import fmi.ethnowear.config.WorkerApiProperties;
import fmi.ethnowear.domain.model.document.processing.JobType;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkerJobClaimService {

    private final WorkerJobClaimStore claimStore;
    private final WorkerClaimTokenService claimTokenService;
    private final WorkerApiProperties properties;
    private final Clock clock;

    @Transactional
    public Optional<WorkerJobClaimDetails> claim(WorkerJobClaimCommand command) {
        if(command == null)
            throw new IllegalArgumentException("Claim command is required");

        Duration lease = resolveLease(command.leaseSeconds());
        LocalDateTime claimedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        WorkerClaimToken token = claimTokenService.generate();

        Set<JobType> supportedJobTypes = command.supportedJobTypes()
                .stream()
                .map(WorkerJobType::toDomainType)
                .collect(Collectors.toUnmodifiableSet());

        return claimStore.claimNext(
                supportedJobTypes,
                command.workerId(),
                claimedAt,
                claimedAt.plus(lease),
                claimedAt.plus(properties.jobTimeout()),
                token.hash()
        ).map(job -> toDetails(job, token.value()));
    }

    private @NonNull Duration resolveLease(Integer leaseSeconds) {
        Duration lease = leaseSeconds == null
                ? properties.defaultLease()
                : Duration.ofSeconds(leaseSeconds);

        if(lease.compareTo(properties.minimumLease()) < 0)
            throw new IllegalArgumentException("Lease is shorter than the minimum allowed lease");

        if(lease.compareTo(properties.maximumLease()) > 0)
            throw new IllegalArgumentException("Lease exceeds the maximum allowed lease");

        return lease;
    }

    @Contract("_, _ -> new")
    private @NonNull WorkerJobClaimDetails toDetails(@NonNull ClaimedWorkerJob job, String claimToken) {
        return new WorkerJobClaimDetails(
                job.jobId(),
                WorkerJobType.valueOf(job.jobType().name()),
                claimToken,
                job.attempt(),
                job.claimedAt().toInstant(ZoneOffset.UTC),
                job.leaseExpiresAt().toInstant(ZoneOffset.UTC),
                new WorkerJobTargetDetails(
                        job.documentId(),
                        job.documentPageId(),
                        job.knowledgeChunkId(),
                        job.inputAvailable()
                ),
                resourceLimits()
        );
    }

    @Contract(" -> new")
    private @NonNull WorkerResourceLimitsDetails resourceLimits() {
        return new WorkerResourceLimitsDetails(
                properties.maximumInputSize().toBytes(),
                properties.maximumPageCount(),
                properties.renderDpi(),
                properties.maximumPixelWidth(),
                properties.maximumPixelHeight(),
                properties.maximumRenditionSize().toBytes(),
                properties.jobTimeout().toSeconds(),
                properties.heartbeatInterval().toSeconds(),
                properties.maximumLease().toSeconds()
        );
    }
}
