package fmi.ethnowear.application.model.worker;

import fmi.ethnowear.domain.model.document.processing.JobType;

import java.time.LocalDateTime;

public record ClaimedWorkerJob(
        long jobId,
        JobType jobType,
        Long documentId,
        Long documentPageId,
        Long knowledgeChunkId,
        boolean inputAvailable,
        int attempt,
        LocalDateTime claimedAt,
        LocalDateTime leaseExpiresAt
) {
}