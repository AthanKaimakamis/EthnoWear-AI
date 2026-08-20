package fmi.ethnowear.application.dto.document.query;

import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;

public record DocumentQueryDto(
        String searchText,
        DocumentType documentType,
        ProvenanceStatus provenanceStatus,
        ProvenanceTrustState provenanceTrustState,
        ProcessingState processingState,
        ReviewState reviewState,
        IndexingState indexingState,
        String language,
        Long sourceId

) {
}
