package fmi.ethnowear.application.dto.document.query.processing;

import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;

public record ProcessingJobQueryDto(
        String searchText,
        JobType jobType,
        JobStatus status,
        Long documentId,
        Long documentPageId
) {
}
