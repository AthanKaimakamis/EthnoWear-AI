package fmi.ethnowear.application.dto.document.query;

import fmi.ethnowear.domain.model.document.indexing.IndexingState;

import java.util.Map;

public record DocumentIndexingStatusDetails(
        Long documentId,
        IndexingState documentState,
        long totalChunks,
        Map<IndexingState, Long> chunkCounts
) {

    public DocumentIndexingStatusDetails {
        chunkCounts = Map.copyOf(chunkCounts);
    }
}
