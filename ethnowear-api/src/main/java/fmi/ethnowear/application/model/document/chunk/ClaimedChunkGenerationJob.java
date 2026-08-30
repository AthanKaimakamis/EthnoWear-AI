package fmi.ethnowear.application.model.document.chunk;

public record ClaimedChunkGenerationJob(
        Long jobId,
        Long documentId,
        String generationInputHash
) {
}
