package fmi.ethnowear.application.port.retrieval;

public record VectorSearchCandidate(
        Long knowledgeChunkId,
        double similarity,
        String indexedContentHash
) {
}