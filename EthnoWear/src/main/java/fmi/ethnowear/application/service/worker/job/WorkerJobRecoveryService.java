package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.port.worker.WorkerJobRecoveryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class WorkerJobRecoveryService {

    private static final int RECOVERY_BATCH_SIZE = 100;

    private final WorkerJobRecoveryStore recoveryStore;
    private final Clock clock;

    @Transactional
    public int recoverExpiredClaims() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return recoveryStore.recoverExpired(now, RECOVERY_BATCH_SIZE);
    }
}