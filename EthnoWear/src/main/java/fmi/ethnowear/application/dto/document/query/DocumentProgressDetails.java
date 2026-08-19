package fmi.ethnowear.application.dto.document.query;

import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;

import java.util.Map;

public record DocumentProgressDetails(
        long totalPages,
        Map<ProcessingState, Long> processing,
        Map<ReviewState, Long> review,
        Map<TranscriptionApprovalState, Long> transcriptionApproval,
        Map<IndexingState, Long> indexing
) {
    public DocumentProgressDetails {
        processing = Map.copyOf(processing);
        review = Map.copyOf(review);
        transcriptionApproval = Map.copyOf(transcriptionApproval);
        indexing = Map.copyOf(indexing);
    }
}
