package fmi.ethnowear.application.dto.document.query.history;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.application.dto.document.query.processing.ProcessingJobCapabilitiesDetails;
import fmi.ethnowear.application.dto.document.query.processing.ProcessingJobRetirementDetails;

import java.time.LocalDateTime;

public record DocumentProcessingJobDetails(
        Long id,
        Long previousJobId,
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
        LocalDateTime updatedAt,
        ProcessingJobCapabilitiesDetails capabilities,
        ProcessingJobRetirementDetails retirement,
        String versionToken
) implements IdentifiableDto {
    public DocumentProcessingJobDetails(
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
    ) {
        this(
                id,
                null,
                jobType,
                status,
                documentId,
                documentPageId,
                inputMediaAssetId,
                knowledgeChunkId,
                priority,
                attemptCount,
                maxAttempts,
                availableAt,
                claimedAt,
                startedAt,
                finishedAt,
                timeoutAt,
                processorName,
                processorVersion,
                toolName,
                toolVersion,
                errorCode,
                safeErrorMessage,
                cancellationReason,
                createdAt,
                updatedAt,
                new ProcessingJobCapabilitiesDetails(false, false, false, false),
                new ProcessingJobRetirementDetails(false, null, null, null),
                null
        );
    }
}
