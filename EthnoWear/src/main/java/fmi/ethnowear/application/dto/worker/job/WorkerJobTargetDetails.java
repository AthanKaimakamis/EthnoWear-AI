package fmi.ethnowear.application.dto.worker.job;

public record WorkerJobTargetDetails(
        Long documentId,
        Long documentPageId,
        Long knowledgeChunkId,
        boolean inputAvailable
) {
}