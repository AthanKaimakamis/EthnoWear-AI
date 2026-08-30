package fmi.ethnowear.application.service.archive.knowledge;

import fmi.ethnowear.application.dto.archive.knowledge.KnowledgeChunkDetails;
import fmi.ethnowear.application.dto.archive.knowledge.KnowledgeChunkWriteDto;
import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeChunkMapper {

    public void apply(@NonNull KnowledgeChunk chunk,
                      @NonNull KnowledgeChunkWriteDto input,
                      SourceReference sourceReference) {
        chunk.setChunkType(input.chunkType());
        chunk.setOntologyIri(input.ontologyIri());
        chunk.setOntologyLocalName(input.ontologyLocalName());
        chunk.setLanguage(input.language());
        chunk.setContent(input.content());
        chunk.setSourceReference(sourceReference);
    }

    public KnowledgeChunkDetails toDetails(@NonNull KnowledgeChunk chunk) {
        return new KnowledgeChunkDetails(
                chunk.getId(),
                id(chunk.getSourceReference()),
                id(chunk.getDocument()),
                id(chunk.getArchiveItem()),
                chunk.getChunkType(),
                chunk.getOntologyIri(),
                chunk.getOntologyLocalName(),
                chunk.getLanguage(),
                chunk.getContent(),
                chunk.getSourceTextType(),
                chunk.getChunkOrdinal(),
                chunk.getContentHash(),
                chunk.getReviewState(),
                chunk.getTranscriptionApprovalState(),
                chunk.getProvenanceTrustState(),
                chunk.getIndexingState(),
                chunk.getEmbeddingModel(),
                chunk.getEmbeddingDimensions(),
                chunk.getVectorCollection(),
                chunk.getVectorPointId(),
                chunk.getIndexedContentHash(),
                chunk.getIndexedAt(),
                chunk.getIndexingError(),
                id(chunk.getSupersededBy()),
                chunk.getCreatedAt(),
                chunk.getUpdatedAt()
        );
    }

    private Long id(UpdatableEntity entity) {
        return entity == null ? null : entity.getId();
    }
}
