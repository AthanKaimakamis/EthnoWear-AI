package fmi.ethnowear.api.dto.archive;

import fmi.ethnowear.application.enums.KnowledgeChunkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KnowledgeChunkInputDto(
        @NotNull
        KnowledgeChunkType chunkType,

        String ontologyIri,
        String ontologyLocalName,

        @NotBlank
        String language,

        @NotBlank
        String content,

        String embeddingModel,
        String embeddingId
) {
}
