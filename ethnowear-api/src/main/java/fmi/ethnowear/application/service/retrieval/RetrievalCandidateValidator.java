package fmi.ethnowear.application.service.retrieval;

import fmi.ethnowear.application.port.retrieval.VectorSearchCandidate;
import fmi.ethnowear.config.WorkerIndexingProperties;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.EvidenceState;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import fmi.ethnowear.util.ContentHashUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class RetrievalCandidateValidator {

    private final WorkerIndexingProperties indexingProperties;

    public boolean isEligible(
            KnowledgeChunk chunk,
            VectorSearchCandidate candidate,
            List<KnowledgeChunkPage> pageLinks
    ) {
        if (chunk == null
                || chunk.getId() == null
                || !Objects.equals(chunk.getId(), candidate.knowledgeChunkId())
                || chunk.getDocument() == null
                || chunk.getSupersededBy() != null
                || chunk.getIndexingState() != IndexingState.INDEXED
                || chunk.getTranscriptionApprovalState()
                != TranscriptionApprovalState.APPROVED
                || chunk.getContent() == null
                || chunk.getContent().isBlank()
                || pageLinks.isEmpty())
            return false;

        String contentHash = ContentHashUtils.sha256(chunk.getContent());

        if (!contentHash.equals(chunk.getContentHash())
                || !contentHash.equals(chunk.getIndexedContentHash())
                || !contentHash.equals(candidate.indexedContentHash())
                || !indexingProperties.expectedEmbeddingModel()
                .equals(chunk.getEmbeddingModel())
                || chunk.getEmbeddingDimensions() == null
                || indexingProperties.expectedEmbeddingDimensions()
                != chunk.getEmbeddingDimensions()
                || !indexingProperties.expectedVectorCollection()
                .equals(chunk.getVectorCollection())
                || !chunk.getId().toString().equals(chunk.getVectorPointId())
                || !provenanceEligible(chunk))
            return false;

        return pageLinks.stream()
                .allMatch(link -> pageEligible(chunk, link));
    }

    private boolean pageEligible(
            KnowledgeChunk chunk,
            @NonNull KnowledgeChunkPage link
    ) {
        DocumentPage page = link.getDocumentPage();

        return page != null
                && Objects.equals(
                page.getDocument().getId(),
                chunk.getDocument().getId()
        )
                && page.getEvidenceState() == EvidenceState.ACTIVE
                && page.getProcessingState() == ProcessingState.COMPLETED
                && page.getReviewState() == ReviewState.APPROVED
                && page.getTranscriptionApprovalState()
                == TranscriptionApprovalState.APPROVED
                && page.getIndexingState() == IndexingState.INDEXED
                && page.getCorrectedText() != null
                && !page.getCorrectedText().isBlank()
                && ContentHashUtils.sha256(page.getCorrectedText())
                .equals(page.getCorrectedTextHash())
                && pageProvenanceEligible(chunk, page);
    }

    private boolean provenanceEligible(@NonNull KnowledgeChunk chunk) {
        if (chunk.getChunkType() == KnowledgeChunkType.STANDALONE_EVIDENCE)
            return chunk.getProvenanceTrustState()
                    != ProvenanceTrustState.UNTRUSTED;

        return chunk.getSourceReference() != null
                && trusted(chunk.getProvenanceTrustState());
    }

    private boolean pageProvenanceEligible(
            @NonNull KnowledgeChunk chunk,
            DocumentPage page
    ) {
        DocumentType type = chunk.getDocument().getDocumentType();

        if (type == DocumentType.STANDALONE_CAPTURE
                || type == DocumentType.UNKNOWN_FRAGMENT_SET)
            return page.getProvenanceTrustState()
                    != ProvenanceTrustState.UNTRUSTED;

        return page.getSourceReference() != null
                && trusted(page.getProvenanceTrustState());
    }

    private boolean trusted(ProvenanceTrustState state) {
        return state == ProvenanceTrustState.TRUSTED
                || state == ProvenanceTrustState.VERIFIED;
    }
}
