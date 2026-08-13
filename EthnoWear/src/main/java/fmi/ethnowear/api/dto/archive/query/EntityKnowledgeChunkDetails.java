package fmi.ethnowear.api.dto.archive.query;

import fmi.ethnowear.application.enums.KnowledgeChunkType;

public record EntityKnowledgeChunkDetails(
        Long id,
        KnowledgeChunkType chunkType,
        String language,
        String content,
        Long sourceReferenceId
) {
}