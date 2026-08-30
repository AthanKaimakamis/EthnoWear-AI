package fmi.ethnowear.application.dto.document.query.processing;

import fmi.ethnowear.domain.model.document.processing.JobStatus;

import java.time.LocalDateTime;

public record ProcessingJobAttemptDetails(
        Long id,
        int executionNumber,
        int attemptNumber,
        JobStatus status,
        String worker,
        LocalDateTime claimedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String processorName,
        String processorVersion,
        String toolName,
        String toolVersion,
        ProcessingJobErrorDetails error,
        String cancellationReason
) {
}
