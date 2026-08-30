package fmi.ethnowear.application.dto.document.query.chunk;

import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.indexing.SourceTextType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;

import java.time.LocalDateTime;
import java.util.List;

public record GeneratedKnowledgeChunkDetails(
        Long id,
        Long documentId,
        Long sourceReferenceId,
        KnowledgeChunkType chunkType,
        String language,
        String content,
        SourceTextType sourceTextType,
        Integer chunkOrdinal,
        String chunkingStrategy,
        String chunkingVersion,
        ReviewState reviewState,
        TranscriptionApprovalState transcriptionApprovalState,
        ProvenanceTrustState provenanceTrustState,
        IndexingState indexingState,
        Long supersededByKnowledgeChunkId,
        boolean current,
        LocalDateTime createdAt,
        List<GeneratedChunkCitationDetails> citations
) {

    public GeneratedKnowledgeChunkDetails {
        citations = List.copyOf(citations);
    }
}
