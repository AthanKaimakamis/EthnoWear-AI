package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.dto.document.query.processing.ProcessingJobCapabilitiesDetails;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ProcessingJobCapabilitiesPolicy {

    private static final Set<JobStatus> RETRYABLE = Set.of(
            JobStatus.FAILED,
            JobStatus.TIMED_OUT,
            JobStatus.CANCELLED,
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

    public ProcessingJobCapabilitiesDetails capabilities(
            DocumentProcessingJob job
    ) {
        boolean retired = job.getRetiredAt() != null;
        boolean cloneableType = job.getJobType() == JobType.OCR
                || job.getJobType() == JobType.OCR_QUALITY_ASSESSMENT
                || job.getJobType() == JobType.VISION_OCR_ASSESSMENT
                || job.getJobType() == JobType.EXTRACT_PAGE_FIGURES;

        return new ProcessingJobCapabilitiesDetails(
                !retired && RETRYABLE.contains(job.getStatus()),
                !retired && cloneableType && TERMINAL.contains(job.getStatus()),
                !retired && CANCELLABLE.contains(job.getStatus()),
                !retired
                        && job.getStatus() != JobStatus.SUCCEEDED
                        && TERMINAL.contains(job.getStatus())
        );
    }
}
