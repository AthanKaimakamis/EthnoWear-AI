package fmi.ethnowear.application.model.document.chunk;

import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;

import java.util.List;

public record ChunkGenerationResult(
        String generationInputHash,
        boolean existingGeneration,
        List<KnowledgeChunk> chunks
) {

    public ChunkGenerationResult {
        chunks = List.copyOf(chunks);
    }
}
