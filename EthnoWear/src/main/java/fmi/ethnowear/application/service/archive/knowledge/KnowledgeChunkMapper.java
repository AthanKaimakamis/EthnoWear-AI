package fmi.ethnowear.application.service.archive.knowledge;

import fmi.ethnowear.api.dto.archive.knowledge.KnowledgeChunkDetails;
import fmi.ethnowear.api.dto.archive.knowledge.KnowledgeChunkWriteDto;
import fmi.ethnowear.dal.entity.KnowledgeChunk;
import fmi.ethnowear.dal.entity.SourceReference;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeChunkMapper {

    public void apply(@NonNull KnowledgeChunk chunk, @NonNull KnowledgeChunkWriteDto input, SourceReference reference) {
        chunk.setChunkType(input.chunkType());
        chunk.setOntologyIri(input.ontologyIri());
        chunk.setOntologyLocalName(input.ontologyLocalName());
        chunk.setLanguage(input.language());
        chunk.setContent(input.content());
        chunk.setSourceReference(reference);
        chunk.setEmbeddingModel(input.embeddingModel());
        chunk.setEmbeddingId(input.embeddingId());
    }

    public KnowledgeChunkDetails toDetails(@NonNull KnowledgeChunk chunk) {
        Long referenceId = chunk.getSourceReference() == null
                ? null
                : chunk.getSourceReference().getId();

        return new KnowledgeChunkDetails(
                chunk.getId(),
                chunk.getChunkType(),
                chunk.getOntologyIri(),
                chunk.getOntologyLocalName(),
                chunk.getLanguage(),
                chunk.getContent(),
                referenceId,
                chunk.getEmbeddingModel(),
                chunk.getEmbeddingId(),
                chunk.getCreatedAt(),
                chunk.getUpdatedAt()
        );
    }
}
