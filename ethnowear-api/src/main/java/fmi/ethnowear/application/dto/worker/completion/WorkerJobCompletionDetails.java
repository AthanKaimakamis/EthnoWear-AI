package fmi.ethnowear.application.dto.worker.completion;

import fmi.ethnowear.application.dto.worker.job.WorkerJobType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "jobId": 20002,
          "jobType": "PAGE_EXTRACTION",
          "documentId": 20002,
          "documentPageId": null,
          "pageCount": 2,
          "queuedOcrJobs": 2,
          "queuedQualityAssessmentJobs": 0,
          "existing": false
        }
        """)
public record WorkerJobCompletionDetails(
        long jobId,
        WorkerJobType jobType,
        Long documentId,
        Long documentPageId,
        int pageCount,
        int queuedOcrJobs,
        int queuedQualityAssessmentJobs,
        int queuedVisionAssessmentJobs,
        boolean existing
) {

    public WorkerJobCompletionDetails(
            long jobId,
            WorkerJobType jobType,
            Long documentId,
            Long documentPageId,
            int pageCount,
            int queuedOcrJobs,
            int queuedQualityAssessmentJobs,
            boolean existing
    ) {
        this(
                jobId,
                jobType,
                documentId,
                documentPageId,
                pageCount,
                queuedOcrJobs,
                queuedQualityAssessmentJobs,
                0,
                existing
        );
    }
}
