package fmi.ethnowear.application.dto.worker.indexing;

import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;

public record WorkerIndexingContextDetails(
        long jobId,
        long knowledgeChunkId,
        String content,
        String contentHash,
        String language,
        KnowledgeChunkType chunkType,
        Long documentId,
        Long sourceReferenceId,
        Long archiveItemId,
        String ontologyIri,
        ProvenanceTrustState provenanceTrustState,
        TranscriptionApprovalState transcriptionApprovalState
) {
}
