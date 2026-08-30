package fmi.ethnowear.application.dto.worker.indexing;

public record WorkerIndexResultDetails(
        long jobId,
        long knowledgeChunkId,
        boolean existing
) {
}
