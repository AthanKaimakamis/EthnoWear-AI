package fmi.ethnowear.application.dto.worker.failure;

import fmi.ethnowear.domain.model.document.processing.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(example = """
        {
          "jobId": 20002,
          "status": "RETRY_WAIT",
          "availableAt": "2026-08-21T08:05:00Z",
          "existing": false
        }
        """)
public record WorkerJobFailureDetails(
        long jobId,
        JobStatus status,
        Instant availableAt,
        boolean existing
) {
}