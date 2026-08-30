package fmi.ethnowear.application.dto.document.query.processing;

import java.time.LocalDateTime;

public record ProcessingJobTimingDetails(
        LocalDateTime availableAt,
        LocalDateTime claimedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime timeoutAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
