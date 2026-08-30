package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.application.dto.document.query.processing.*;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJobAttempt;
import fmi.ethnowear.application.service.document.processing.ProcessingJobCapabilitiesPolicy;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.List;

@Component
public class ProcessingJobAdminMapper {

    private final ProcessingJobCapabilitiesPolicy capabilitiesPolicy =
            new ProcessingJobCapabilitiesPolicy();

    private static final Set<JobStatus> RETRYABLE = Set.of(
            JobStatus.FAILED,
            JobStatus.CANCELLED,
            JobStatus.TIMED_OUT,
            JobStatus.DEAD
    );

    private static final Set<JobStatus> CANCELLABLE = Set.of(
            JobStatus.QUEUED,
            JobStatus.RETRY_WAIT,
            JobStatus.CLAIMED,
            JobStatus.RUNNING,
            JobStatus.CANCEL_REQUESTED
    );

    private static final Set<JobStatus> TERMINAL = Set.of(
            JobStatus.SUCCEEDED,
            JobStatus.FAILED,
            JobStatus.CANCELLED,
            JobStatus.TIMED_OUT,
            JobStatus.DEAD
    );

    public ProcessingJobSummaryDetails toDetails(@NonNull DocumentProcessingJob job) {
        return toDetails(
                job,
                List.of(),
                new ProcessingJobResultDetails(List.of(), null, null)
        );
    }

    public ProcessingJobSummaryDetails toDetails(
            @NonNull DocumentProcessingJob job,
            List<DocumentProcessingJobAttempt> attempts,
            ProcessingJobResultDetails result
    ) {
        DocumentPage page = job.getDocumentPage();
        Document document = job.getDocument() == null && page != null
                ? page.getDocument()
                : job.getDocument();
        int attemptCount = job.getAttemptCount() == null ? 0 : job.getAttemptCount();
        int maxAttempts = job.getMaxAttempts() == null ? 0 : job.getMaxAttempts();

        return new ProcessingJobSummaryDetails(
                job.getId(),
                job.getPreviousJob() == null
                        ? null
                        : job.getPreviousJob().getId(),
                job.getJobType(),
                job.getStatus(),
                new ProcessingJobPurposeDetails(
                        job.getJobType(),
                        job.getJobType().name()
                ),
                target(job),
                job.getPriority(),
                new ProcessingJobProgressDetails(
                        attemptCount,
                        maxAttempts,
                        Math.max(0, maxAttempts - attemptCount),
                        RETRYABLE.contains(job.getStatus()),
                        CANCELLABLE.contains(job.getStatus()),
                        TERMINAL.contains(job.getStatus())
                ),
                new ProcessingJobTimingDetails(
                        job.getAvailableAt(),
                        job.getClaimedAt(),
                        job.getStartedAt(),
                        job.getFinishedAt(),
                        job.getTimeoutAt(),
                        job.getCreatedAt(),
                        job.getUpdatedAt()
                ),
                error(job),
                job.getCancellationReason(),
                document == null
                        ? null
                        : new ProcessingJobDocumentContextDetails(
                                document.getId(),
                                document.getTitle(),
                                document.getLanguage()
                        ),
                page == null
                        ? null
                        : new ProcessingJobPageContextDetails(
                                page.getId(),
                                page.getPageSequence(),
                                page.getPdfPageIndex(),
                                page.getPrintedPageNumber(),
                                page.getPageLabel()
                        ),
                job.getInputMediaAsset() == null
                        ? null
                        : job.getInputMediaAsset().getId(),
                job.getKnowledgeChunk() == null
                        ? null
                        : job.getKnowledgeChunk().getId(),
                attempts.stream()
                        .map(this::toDetails)
                        .toList(),
                result,
                capabilitiesPolicy.capabilities(job),
                new ProcessingJobRetirementDetails(
                        job.getRetiredAt() != null,
                        job.getRetiredAt(),
                        job.getRetiredBy(),
                        job.getRetirementReason()
                ),
                fmi.ethnowear.util.RowVersionUtils.token(job.getRowVersion())
        );
    }

    private ProcessingJobTargetDetails target(
            @NonNull DocumentProcessingJob job
    ) {
        String type;

        if (job.getDocumentPage() != null)
            type = "DOCUMENT_PAGE";
        else if (job.getKnowledgeChunk() != null)
            type = "KNOWLEDGE_CHUNK";
        else if (job.getDocument() != null)
            type = "DOCUMENT";
        else
            type = "MEDIA_ASSET";

        return new ProcessingJobTargetDetails(
                type,
                job.getDocument() == null ? null : job.getDocument().getId(),
                job.getDocumentPage() == null ? null : job.getDocumentPage().getId(),
                job.getInputMediaAsset() == null ? null : job.getInputMediaAsset().getId(),
                job.getKnowledgeChunk() == null ? null : job.getKnowledgeChunk().getId()
        );
    }

    private ProcessingJobAttemptDetails toDetails(
            @NonNull DocumentProcessingJobAttempt attempt
    ) {
        ProcessingJobErrorDetails attemptError =
                attempt.getErrorCode() == null
                        && attempt.getSafeErrorMessage() == null
                        ? null
                        : new ProcessingJobErrorDetails(
                                attempt.getErrorCode(),
                                attempt.getSafeErrorMessage()
                        );

        return new ProcessingJobAttemptDetails(
                attempt.getId(),
                attempt.getExecutionNumber(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getClaimedBy(),
                attempt.getClaimedAt(),
                attempt.getStartedAt(),
                attempt.getFinishedAt(),
                attempt.getProcessorName(),
                attempt.getProcessorVersion(),
                attempt.getToolName(),
                attempt.getToolVersion(),
                attemptError,
                attempt.getCancellationReason()
        );
    }

    private ProcessingJobErrorDetails error(@NonNull DocumentProcessingJob job) {
        if (job.getErrorCode() == null && job.getSafeErrorMessage() == null)
            return null;

        return new ProcessingJobErrorDetails(
                job.getErrorCode(),
                job.getSafeErrorMessage()
        );
    }
}
