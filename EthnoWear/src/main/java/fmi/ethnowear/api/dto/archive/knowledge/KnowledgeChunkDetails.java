package fmi.ethnowear.api.dto.archive.knowledge;

import fmi.ethnowear.api.dto.IdentifiableDto;
import fmi.ethnowear.application.enums.KnowledgeChunkType;

import java.time.LocalDateTime;

public record KnowledgeChunkDetails(
        Long id,
        KnowledgeChunkType chunkType,
        String ontologyIri,
        String ontologyLocalName,
        String language,
        String content,
        Long sourceReferenceId,
        String embeddingModel,
        String embeddingId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {
}
