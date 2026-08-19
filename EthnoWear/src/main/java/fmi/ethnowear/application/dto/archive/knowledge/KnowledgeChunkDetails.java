package fmi.ethnowear.application.dto.archive.knowledge;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.indexing.SourceTextType;
import fmi.ethnowear.domain.model.document.review.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;

import java.time.LocalDateTime;

public record KnowledgeChunkDetails(
        Long id,
        Long sourceReferenceId,
        Long documentId,
        Long archiveItemId,
        KnowledgeChunkType chunkType,
        String ontologyIri,
        String ontologyLocalName,
        String language,
        String content,
        SourceTextType sourceTextType,
        Integer chunkOrdinal,
        String contentHash,
        ReviewState reviewState,
        TranscriptionApprovalState transcriptionApprovalState,
        ProvenanceTrustState provenanceTrustState,
        IndexingState indexingState,
        String embeddingModel,
        Integer embeddingDimensions,
        String vectorCollection,
        String vectorPointId,
        String indexedContentHash,
        LocalDateTime indexedAt,
        String indexingError,
        Long supersededByKnowledgeChunkId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {
}
