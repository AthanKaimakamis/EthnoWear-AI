package fmi.ethnowear.application.dto.document.query.processing;

import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;

import java.util.List;

public record ProcessingJobBulkRetryDetails(
        List<DocumentProcessingJobDetails> jobs
) {

    public ProcessingJobBulkRetryDetails {
        jobs = List.copyOf(jobs);
    }
}
