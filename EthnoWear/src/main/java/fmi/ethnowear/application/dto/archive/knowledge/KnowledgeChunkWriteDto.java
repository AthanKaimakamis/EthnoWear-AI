package fmi.ethnowear.application.dto.archive.knowledge;

import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KnowledgeChunkWriteDto(
        @NotNull
        KnowledgeChunkType chunkType,

        @Size(max = 1000)
        String ontologyIri,

        @Size(max = 200)
        String ontologyLocalName,

        @NotBlank
        @Size(max = 10)
        String language,

        @NotBlank
        String content,

        Long sourceReferenceId,

        @Size(max = 100)
        String embeddingModel,

        @Size(max = 255)
        String embeddingId
) {
}
