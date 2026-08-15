package fmi.ethnowear.application.dto.archive.knowledge;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;

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
