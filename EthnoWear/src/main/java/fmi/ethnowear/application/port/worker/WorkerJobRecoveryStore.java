package fmi.ethnowear.application.port.worker;

import java.time.LocalDateTime;

public interface WorkerJobRecoveryStore {

    int recoverExpired(LocalDateTime now, int batchSize);
}