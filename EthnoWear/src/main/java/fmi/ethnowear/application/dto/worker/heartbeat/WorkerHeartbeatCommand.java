package fmi.ethnowear.application.dto.worker.heartbeat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

@Schema(example = """
        {
          "leaseSeconds": 120
        }
        """)
public record WorkerHeartbeatCommand(
        @Positive
        Integer leaseSeconds
) {
}
