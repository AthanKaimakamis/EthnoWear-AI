package fmi.ethnowear.application.dto.document.query.processing;

import fmi.ethnowear.domain.model.document.processing.JobStatus;

import java.util.Map;

public record ProcessingJobCountsDetails(
        long total,
        long active,
        long retryable,
        Map<JobStatus, Long> byStatus
) {

    public ProcessingJobCountsDetails {
        byStatus = Map.copyOf(byStatus);
    }
}
