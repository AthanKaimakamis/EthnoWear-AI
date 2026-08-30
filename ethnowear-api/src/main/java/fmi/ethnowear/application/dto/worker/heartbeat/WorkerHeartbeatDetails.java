package fmi.ethnowear.application.dto.worker.heartbeat;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(example = """
        {
          "leaseExpiresAt": "2026-08-21T08:03:00Z",
          "cancellationRequested": false
        }
        """)
public record WorkerHeartbeatDetails(
        Instant leaseExpiresAt,
        boolean cancellationRequested
) {
}
