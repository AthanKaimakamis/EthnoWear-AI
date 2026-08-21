package fmi.ethnowear.application.service.worker.job;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "ethnowear.worker-api",
        name = "enabled",
        havingValue = "true"
)
public class WorkerJobRecoveryScheduler {

    private final WorkerJobRecoveryService recoveryService;

    @Scheduled(
            fixedDelayString = "${ethnowear.worker-api.recovery-interval}",
            initialDelayString = "${ethnowear.worker-api.recovery-interval}"
    )
    public void recoverExpiredClaims() {
        recoveryService.recoverExpiredClaims();
    }
}