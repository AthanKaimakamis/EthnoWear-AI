package fmi.ethnowear.application.dto.document.query;

import fmi.ethnowear.application.dto.document.query.history.BoundedHistoryDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;

public record DocumentDetails(
        DocumentSummaryDetails summary,
        DocumentSourceDetails source,
        String notes,
        Long mergedIntoDocumentId,
        DocumentProgressDetails progress,
        DocumentIndexingStatusDetails indexingStatus,
        BoundedHistoryDetails<DocumentProcessingJobDetails> recentJobs
) {
}
