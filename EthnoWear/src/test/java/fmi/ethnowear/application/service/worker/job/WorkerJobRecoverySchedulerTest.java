package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.port.worker.WorkerJobRecoveryStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkerJobRecoverySchedulerTest {

    @Test
    void delegatesExpiredClaimRecovery() {
        RecordingRecoveryStore store = new RecordingRecoveryStore();
        WorkerJobRecoveryService recoveryService = new WorkerJobRecoveryService(
                store,
                Clock.fixed(Instant.parse("2026-08-21T06:00:00Z"), ZoneOffset.UTC)
        );
        WorkerJobRecoveryScheduler scheduler = new WorkerJobRecoveryScheduler(recoveryService);

        scheduler.recoverExpiredClaims();

        assertEquals(LocalDateTime.of(2026, 8, 21, 6, 0), store.now);
        assertEquals(100, store.batchSize);
    }

    private static class RecordingRecoveryStore implements WorkerJobRecoveryStore {

        private LocalDateTime now;
        private int batchSize;

        @Override
        public int recoverExpired(LocalDateTime now, int batchSize) {
            this.now = now;
            this.batchSize = batchSize;
            return 1;
        }
    }
}
