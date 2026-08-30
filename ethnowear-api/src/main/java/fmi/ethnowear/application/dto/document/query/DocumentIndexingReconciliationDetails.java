package fmi.ethnowear.application.dto.document.query;

import fmi.ethnowear.domain.model.document.indexing.IndexingState;

public record DocumentIndexingReconciliationDetails(
        Long documentId,
        IndexingState previousDocumentState,
        IndexingState documentState,
        int changedPageCount,
        boolean changed
) {
}
