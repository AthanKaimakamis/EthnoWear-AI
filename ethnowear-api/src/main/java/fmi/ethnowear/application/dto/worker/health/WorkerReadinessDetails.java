package fmi.ethnowear.application.dto.worker.health;

public record WorkerReadinessDetails(
        String status,
        boolean workerApiEnabled
) {
}
