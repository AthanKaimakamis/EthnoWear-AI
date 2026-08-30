package fmi.ethnowear.application.dto.worker.indexing;

import java.util.List;

public record WorkerEmbeddingDetails(
        long jobId,
        long knowledgeChunkId,
        String contentHash,
        String embeddingModel,
        int embeddingDimensions,
        List<Float> values
) {

    public WorkerEmbeddingDetails {
        values = List.copyOf(values);
    }
}
