package fmi.ethnowear.application.dto.document.query;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.document.EvidenceState;
import fmi.ethnowear.domain.model.document.PageKind;
import fmi.ethnowear.domain.model.document.PageRole;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DocumentPageSummaryDetails(
        Long id,
        Long documentId,
        Long sourceReferenceId,
        PageKind pageKind,
        PageRole pageRole,
        Integer pageSequence,
        Integer pdfPageIndex,
        String printedPageNumber,
        Integer printedPageSort,
        String pageLabel,
        ProvenanceStatus provenanceStatus,
        ProcessingState processingState,
        ReviewState reviewState,
        TranscriptionApprovalState transcriptionApprovalState,
        ProvenanceTrustState provenanceTrustState,
        IndexingState indexingState,
        EvidenceState evidenceState,
        BigDecimal ocrConfidence,
        boolean hasRawOcrText,
        boolean hasCorrectedText,
        Long previewMediaAssetId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {
}
