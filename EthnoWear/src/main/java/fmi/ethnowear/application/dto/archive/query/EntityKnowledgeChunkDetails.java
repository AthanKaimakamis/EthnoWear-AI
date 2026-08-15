package fmi.ethnowear.application.dto.archive.query;

import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;

public record EntityKnowledgeChunkDetails(
        Long id,
        KnowledgeChunkType chunkType,
        String language,
        String content,
        Long sourceReferenceId
) {
}