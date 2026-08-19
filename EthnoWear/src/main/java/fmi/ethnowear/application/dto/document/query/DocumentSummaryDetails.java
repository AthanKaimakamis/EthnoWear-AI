package fmi.ethnowear.application.dto.document.query;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.review.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;

import java.time.LocalDateTime;

public record DocumentSummaryDetails(
        Long id,
        Long sourceId,
        String sourceTitle,
        Long originalMediaAssetId,
        DocumentType documentType,
        ProvenanceStatus provenanceStatus,
        String title,
        String author,
        String publisher,
        Integer publicationYear,
        String language,
        Integer pageCount,
        ProcessingState processingState,
        ReviewState reviewState,
        ProvenanceTrustState provenanceTrustState,
        IndexingState indexingState,
        DocumentProgressSummaryDetails progress,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {
}
