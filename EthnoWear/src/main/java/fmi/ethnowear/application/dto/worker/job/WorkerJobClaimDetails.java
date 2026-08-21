package fmi.ethnowear.application.dto.worker.job;

import fmi.ethnowear.application.dto.worker.WorkerResourceLimitsDetails;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(example = """
        {
          "jobId": 20002,
          "jobType": "PAGE_EXTRACTION",
          "claimToken": "opaque-attempt-token",
          "attempt": 1,
          "claimedAt": "2026-08-21T08:00:00Z",
          "leaseExpiresAt": "2026-08-21T08:02:00Z",
          "target": {
            "documentId": 20002,
            "documentPageId": null,
            "knowledgeChunkId": null,
            "inputAvailable": true
          }
        }
        """)
public record WorkerJobClaimDetails(
        long jobId,
        WorkerJobType jobType,
        String claimToken,
        int attempt,
        Instant claimedAt,
        Instant leaseExpiresAt,
        WorkerJobTargetDetails target,
        WorkerResourceLimitsDetails limits
) {
}