package fmi.ethnowear.application.dto.document.query.processing;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;

public record ProcessingJobSummaryDetails(
        Long id,
        Long previousJobId,
        JobType jobType,
        JobStatus status,
        ProcessingJobPurposeDetails purpose,
        ProcessingJobTargetDetails target,
        Integer priority,
        ProcessingJobProgressDetails progress,
        ProcessingJobTimingDetails timestamps,
        ProcessingJobErrorDetails error,
        String cancellationReason,
        ProcessingJobDocumentContextDetails document,
        ProcessingJobPageContextDetails page,
        Long inputMediaAssetId,
        Long knowledgeChunkId,
        java.util.List<ProcessingJobAttemptDetails> attempts,
        ProcessingJobResultDetails result,
        ProcessingJobCapabilitiesDetails capabilities,
        ProcessingJobRetirementDetails retirement,
        String versionToken
) implements IdentifiableDto {
    public ProcessingJobSummaryDetails {
        attempts = java.util.List.copyOf(attempts);
    }

    public ProcessingJobSummaryDetails(
            Long id,
            JobType jobType,
            JobStatus status,
            Integer priority,
            ProcessingJobProgressDetails progress,
            ProcessingJobTimingDetails timestamps,
            ProcessingJobErrorDetails error,
            String cancellationReason,
            ProcessingJobDocumentContextDetails document,
            ProcessingJobPageContextDetails page,
            Long inputMediaAssetId,
            Long knowledgeChunkId
    ) {
        this(
                id,
                null,
                jobType,
                status,
                new ProcessingJobPurposeDetails(jobType, jobType.name()),
                new ProcessingJobTargetDetails(
                        page != null ? "DOCUMENT_PAGE" : "DOCUMENT",
                        document == null ? null : document.id(),
                        page == null ? null : page.id(),
                        inputMediaAssetId,
                        knowledgeChunkId
                ),
                priority,
                progress,
                timestamps,
                error,
                cancellationReason,
                document,
                page,
                inputMediaAssetId,
                knowledgeChunkId,
                java.util.List.of(),
                new ProcessingJobResultDetails(java.util.List.of(), null, null),
                new ProcessingJobCapabilitiesDetails(false, false, false, false),
                new ProcessingJobRetirementDetails(false, null, null, null),
                null
        );
    }
}
