package fmi.ethnowear.application.dto.retrieval;

import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;

import java.util.List;

public record GroundedPassageDetails(
        Long chunkId,
        String excerpt,
        String language,
        KnowledgeChunkType chunkType,
        Long documentId,
        String documentTitle,
        List<GroundedPageCitationDetails> pages,
        double similarity,
        ProvenanceStatus provenanceStatus,
        ProvenanceTrustState provenanceTrustState,
        TranscriptionApprovalState transcriptionApprovalState,
        boolean standaloneEvidence
) {

    public GroundedPassageDetails {
        pages = List.copyOf(pages);
    }
}
