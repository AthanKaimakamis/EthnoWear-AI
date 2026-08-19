package fmi.ethnowear.application.dto.document.query;

public record DocumentProgressSummaryDetails(
        long totalPages,
        long completedProcessingPages,
        long failedProcessingPages,
        long reviewRequiredPages,
        long approvedTranscriptionPages,
        long indexedPages
) {
}
