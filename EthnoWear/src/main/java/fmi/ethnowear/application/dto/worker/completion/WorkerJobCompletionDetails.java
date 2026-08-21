package fmi.ethnowear.application.dto.worker.completion;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "jobId": 20002,
          "documentId": 20002,
          "pageCount": 2,
          "queuedOcrJobs": 2,
          "existing": false
        }
        """)
public record WorkerJobCompletionDetails(
        long jobId,
        long documentId,
        int pageCount,
        int queuedOcrJobs,
        boolean existing
) {
}