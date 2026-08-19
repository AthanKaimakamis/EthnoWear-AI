package fmi.ethnowear.application.dto.document.query.history;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;

import java.time.LocalDateTime;

public record DocumentProcessingJobDetails(
        Long id,
        JobType jobType,
        JobStatus status,
        Long documentId,
        Long documentPageId,
        Long inputMediaAssetId,
        Long knowledgeChunkId,
        Integer priority,
        Integer attemptCount,
        Integer maxAttempts,
        LocalDateTime availableAt,
        LocalDateTime claimedAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime timeoutAt,
        String processorName,
        String processorVersion,
        String toolName,
        String toolVersion,
        String errorCode,
        String safeErrorMessage,
        String cancellationReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {
}
