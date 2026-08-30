package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.command.processing.DocumentJobCancellationCommand;
import fmi.ethnowear.application.dto.document.command.processing.ProcessingJobBulkRetryCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.dto.document.query.processing.*;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobLifecycleService;
import fmi.ethnowear.application.service.document.processing.DocumentWorkflowManagementService;
import fmi.ethnowear.application.service.document.query.ProcessingJobAdminQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/processing")
@RequiredArgsConstructor
@Tag(name = "Admin processing", description = "Processing monitoring and lifecycle operations")
public class AdminProcessingController {

    private final ProcessingJobAdminQueryService queryService;
    private final DocumentProcessingJobLifecycleService lifecycleService;
    private final DocumentWorkflowManagementService workflowManagementService;

    @Operation(summary = "List processing jobs")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jobs returned"),
            @ApiResponse(responseCode = "400", description = "Invalid filter, page, or sort"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Role is not permitted")
    })
    @GetMapping("/jobs")
    public Page<ProcessingJobSummaryDetails> findAll(
            @ParameterObject @ModelAttribute ProcessingJobQueryDto query,
            @ParameterObject
            @PageableDefault(
                    size = 25,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return queryService.findAll(query, pageable);
    }

    @Operation(
            summary = "Get processing counts",
            description = "Returns counts by state for the supplied filters, excluding the state filter itself."
    )
    @GetMapping("/counts")
    public ProcessingJobCountsDetails counts(
            @ParameterObject @ModelAttribute ProcessingJobQueryDto query
    ) {
        return queryService.counts(query);
    }

    @Operation(summary = "Retry a processing job")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Retry accepted"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "409", description = "Job is not retryable or an active job exists")
    })
    @PostMapping("/jobs/{jobId}/retry")
    public ResponseEntity<DocumentProcessingJobDetails> retry(
            @Parameter(description = "Processing job identifier")
            @PathVariable Long jobId
    ) {
        return ResponseEntity.accepted().body(lifecycleService.retry(jobId));
    }

    @Operation(
            summary = "Rerun multiple processing jobs",
            description = "Reruns up to 100 unique completed, failed, timed-out, or exhausted jobs atomically."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Retries accepted"),
            @ApiResponse(responseCode = "400", description = "Invalid job identifiers"),
            @ApiResponse(responseCode = "404", description = "A job was not found"),
            @ApiResponse(responseCode = "409", description = "A job is not retryable or an active job exists")
    })
    @PostMapping("/jobs/retry")
    public ResponseEntity<ProcessingJobBulkRetryDetails> retryAll(
            @Valid @RequestBody ProcessingJobBulkRetryCommand command
    ) {
        return ResponseEntity.accepted().body(
                new ProcessingJobBulkRetryDetails(
                        lifecycleService.retryAll(command.jobIds())
                )
        );
    }

    @Operation(summary = "Cancel a processing job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job cancelled or cancellation requested"),
            @ApiResponse(responseCode = "400", description = "Invalid cancellation reason"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "409", description = "Job cannot be cancelled from its current state")
    })
    @PostMapping("/jobs/{jobId}/cancel")
    public DocumentProcessingJobDetails cancel(
            @Parameter(description = "Processing job identifier")
            @PathVariable Long jobId,
            @Valid @RequestBody DocumentJobCancellationCommand command
    ) {
        return lifecycleService.cancel(jobId, command);
    }

    @Operation(summary = "Create a replacement OCR or quality job")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Replacement queued"),
            @ApiResponse(responseCode = "400", description = "Invalid replacement request"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "409", description = "Job is stale, active, or not replaceable")
    })
    @PostMapping("/jobs/{jobId}/replacement")
    public ResponseEntity<DocumentProcessingJobDetails> replacement(
            @PathVariable Long jobId,
            @RequestHeader("If-Match") String versionToken
    ) {
        return ResponseEntity.accepted().body(
                workflowManagementService.createReplacementJob(
                        jobId,
                        versionToken
                )
        );
    }

    @Operation(summary = "Retire an obsolete processing job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job retired"),
            @ApiResponse(responseCode = "400", description = "Invalid retirement request"),
            @ApiResponse(responseCode = "404", description = "Job not found"),
            @ApiResponse(responseCode = "409", description = "Job is stale or still authoritative")
    })
    @DeleteMapping("/jobs/{jobId}")
    public DocumentProcessingJobDetails retire(
            @PathVariable Long jobId,
            @RequestHeader("If-Match") String versionToken,
            @RequestParam String reason,
            Principal principal
    ) {
        return workflowManagementService.retireJob(
                jobId,
                versionToken,
                reason,
                principal.getName()
        );
    }
}
