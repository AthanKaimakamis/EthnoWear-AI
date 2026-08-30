package fmi.ethnowear.application.dto.document.query.processing;

import fmi.ethnowear.domain.model.document.processing.JobType;

public record ProcessingJobPurposeDetails(
        JobType type,
        String code
) {
}
