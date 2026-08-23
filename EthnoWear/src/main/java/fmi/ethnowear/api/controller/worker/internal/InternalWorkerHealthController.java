package fmi.ethnowear.api.controller.worker.internal;

import fmi.ethnowear.application.dto.worker.health.WorkerReadinessDetails;
import fmi.ethnowear.application.service.worker.health.WorkerReadinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static fmi.ethnowear.config.OpenApiConfig.WORKER_AUTH;

@Tag(name = "Internal worker health")
@SecurityRequirement(name = WORKER_AUTH)
@RestController
@RequestMapping("/api/internal/worker")
@RequiredArgsConstructor
public class InternalWorkerHealthController {

    private final WorkerReadinessService readinessService;

    @Operation(
            summary = "Check worker API readiness",
            description = "Validates worker authentication, API enablement, database connectivity and permanent media storage without claiming a job."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Worker API is ready"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid worker service token"),
            @ApiResponse(responseCode = "503", description = "A required backend dependency is unavailable")
    })
    @GetMapping("/health")
    public WorkerReadinessDetails health() {
        return readinessService.check();
    }
}
