package fmi.ethnowear.application.dto.worker.cancellation;

import fmi.ethnowear.domain.model.document.processing.JobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(example = """
        {
          "jobId": 20002,
          "status": "CANCELLED",
          "finishedAt": "2026-08-21T08:01:00Z",
          "existing": false
        }
        """)
public record WorkerJobCancellationDetails(
        long jobId,
        JobStatus status,
        Instant finishedAt,
        boolean existing
) {
}