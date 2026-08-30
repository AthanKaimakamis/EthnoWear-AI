package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.port.worker.WorkerJobRecoveryStore;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.service.document.figure.FigureExtractionJobStateService;
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
    private final ManagementEventPublisher managementEvents;
    private final FigureExtractionJobStateService figureJobStateService;

    @Transactional
    public int recoverExpiredClaims() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int recovered = recoveryStore.recoverExpired(now, RECOVERY_BATCH_SIZE);

        if (recovered > 0)
            figureJobStateService.reconcileRecovered(now);

        if (recovered > 0)
            managementEvents.processingJobChanged();

        return recovered;
    }
}
