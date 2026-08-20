package fmi.ethnowear.application.service.document.upload;

import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class DocumentUploadStatePolicy {

    public ProcessingState processingState(boolean queueProcessing) {
        return queueProcessing
                ? ProcessingState.PENDING
                : ProcessingState.UPLOADED;
    }

    public void markPageAdded(@NonNull Document document, boolean queueProcessing) {
        document.setProcessingState(processingState(queueProcessing));

        document.setReviewState(ReviewState.NOT_READY);
        document.setIndexingState(changedIndexingState(document.getIndexingState()));
    }

    public void markPageReprocessing(@NonNull Document document, @NonNull DocumentPage page) {
        document.setProcessingState(ProcessingState.PENDING);
        document.setReviewState(ReviewState.NOT_READY);
        document.setIndexingState(changedIndexingState(document.getIndexingState()));

        page.setProcessingState(ProcessingState.PENDING);
        page.setReviewState(ReviewState.NOT_READY);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.PENDING);

        page.setIndexingState(changedIndexingState(page.getIndexingState()));
    }

    private IndexingState changedIndexingState(IndexingState currentState) {
        if (currentState == IndexingState.INDEXED || currentState == IndexingState.OUTDATED)
            return IndexingState.OUTDATED;

        return IndexingState.NOT_ELIGIBLE;
    }
}