package fmi.ethnowear.api.controller.worker.internal;

import fmi.ethnowear.application.dto.worker.cancellation.WorkerJobCancellationDetails;
import fmi.ethnowear.application.dto.worker.completion.WorkerJobCompletionDetails;
import fmi.ethnowear.application.dto.worker.extraction.*;
import fmi.ethnowear.application.dto.worker.failure.*;
import fmi.ethnowear.application.dto.worker.heartbeat.*;
import fmi.ethnowear.application.dto.worker.job.*;
import fmi.ethnowear.application.dto.worker.rendition.*;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.archive.media.delivery.MediaDelivery;
import fmi.ethnowear.application.service.worker.job.*;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static fmi.ethnowear.config.OpenApiConfig.WORKER_AUTH;

@ApiResponses({
        @ApiResponse(
                responseCode = "401",
                description = "Missing or invalid worker service token"
        ),
        @ApiResponse(
                responseCode = "500",
                description = "Internal processing error"
        )
})
@Tag(
        name = "Internal worker jobs",
        description = "Claim and process document jobs using attempt-specific leases."
)
@SecurityRequirement(name = WORKER_AUTH)
@RestController
@RequestMapping("/api/internal/worker/jobs")
@RequiredArgsConstructor
public class InternalWorkerJobController {

    private final WorkerJobClaimService claimService;
    private final WorkerJobHeartbeatService heartbeatService;
    private final WorkerJobInputService inputService;
    private final PageExtractionManifestService manifestService;
    private final WorkerPageRenditionService renditionService;
    private final WorkerJobCompletionService completionService;
    private final WorkerJobFailureService failureService;
    private final WorkerJobCancellationService cancellationService;

    //region claim
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Claim the next available job",
            description = """
                    Atomically claims one supported queued job. The requested lease must be
                    within the configured minimum and maximum. The returned claim token is
                    valid only for the claimed job and attempt.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job claimed"),
            @ApiResponse(responseCode = "204", description = "No supported job available"),
            @ApiResponse(responseCode = "400", description = "Invalid claim request")
    })
    @PostMapping("/claim")
    public ResponseEntity<WorkerJobClaimDetails> claim(@Valid @RequestBody WorkerJobClaimCommand command) {
        return claimService.claim(command)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
    //---------------------------------------------------------------------------------------
    //endregion


    //region heartbeat
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Renew a job lease",
            description = """
                    Renews the active lease without exceeding the job timeout. Workers should
                    heartbeat at the interval returned in the claim resource limits and stop
                    processing when cancellationRequested is true.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lease renewed"),
            @ApiResponse(responseCode = "400", description = "Invalid heartbeat"),
            @ApiResponse(responseCode = "409", description = "Stale or incorrect claim"),
            @ApiResponse(responseCode = "410", description = "Claim expired")
    })
    @PostMapping("/{jobId}/heartbeat")
    public WorkerHeartbeatDetails heartbeat(@PathVariable
                                            Long jobId,
                                            @Parameter(
                                                    description = "Worker identifier used when the job was claimed.",
                                                    example = "page-extractor-1",
                                                    required = true
                                            )
                                            @RequestHeader("X-Worker-Id")
                                            String workerId,
                                            @Parameter(
                                                    description = "Opaque token valid only for this job and processing attempt.",
                                                    required = true,
                                                    schema = @Schema(type = "string", format = "password")
                                            )
                                            @RequestHeader("X-Worker-Claim-Token")
                                            String claimToken,
                                            @Valid @RequestBody(required = false)
                                            WorkerHeartbeatCommand command
    ) {
        return heartbeatService.heartbeat(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken),
                command
        );
    }
    //---------------------------------------------------------------------------------------
    //endregion


    //region input
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Stream the claimed job input",
            description = "Returns controlled PDF content without exposing storage paths."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF content"),
            @ApiResponse(responseCode = "302", description = "Controlled media redirect"),
            @ApiResponse(responseCode = "404", description = "Input media not found"),
            @ApiResponse(responseCode = "409", description = "Stale or incorrect claim"),
            @ApiResponse(responseCode = "410", description = "Claim expired"),
            @ApiResponse(responseCode = "422", description = "Input is unusable")
    })
    @GetMapping("/{jobId}/input")
    public ResponseEntity<?> input(@PathVariable
                                   Long jobId,
                                   @Parameter(
                                           description = "Worker identifier used when the job was claimed.",
                                           example = "page-extractor-1",
                                           required = true
                                   )
                                   @RequestHeader("X-Worker-Id")
                                   String workerId,
                                   @Parameter(
                                           description = "Opaque token valid only for this job and processing attempt.",
                                           required = true,
                                           schema = @Schema(type = "string", format = "password")
                                   )
                                   @RequestHeader("X-Worker-Claim-Token")
                                   String claimToken
    ) {
        MediaDelivery delivery = inputService.findInput(jobId, new WorkerClaimCredentials(workerId, claimToken));

        if (delivery instanceof MediaDelivery.Redirect(java.net.URI location))
            return ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(location)
                    .build();

        MediaDelivery.Local local = (MediaDelivery.Local) delivery;

        return ResponseEntity.ok()
                .contentType(contentType(local.mimeType()))
                .contentLength(local.contentLength())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(local.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(local.resource());
    }
    //---------------------------------------------------------------------------------------
    //endregion


    //region manifest
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Reconcile the page-extraction manifest",
            description = "Idempotent for an identical manifest; conflicting retries return 409."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Manifest accepted or reconciled"),
            @ApiResponse(responseCode = "400", description = "Invalid manifest"),
            @ApiResponse(responseCode = "409", description = "Conflicting manifest or stale claim"),
            @ApiResponse(responseCode = "410", description = "Claim expired"),
            @ApiResponse(responseCode = "413", description = "Manifest exceeds configured limits")
    })
    @PostMapping("/{jobId}/page-extraction/manifest")
    public PageExtractionManifestDetails manifest(@PathVariable
                                                  Long jobId,
                                                  @Parameter(
                                                          description = "Worker identifier used when the job was claimed.",
                                                          example = "page-extractor-1",
                                                          required = true
                                                  )
                                                  @RequestHeader("X-Worker-Id")
                                                  String workerId,
                                                  @Parameter(
                                                          description = "Opaque token valid only for this job and processing attempt.",
                                                          required = true,
                                                          schema = @Schema(type = "string", format = "password")
                                                  )
                                                  @RequestHeader("X-Worker-Claim-Token")
                                                  String claimToken,
                                                  @Valid @RequestBody
                                                  PageExtractionManifestCommand command
    ) {
        return manifestService.reconcile(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken),
                command
        );
    }
    //---------------------------------------------------------------------------------------
    //endregion


    //region uploadRendition
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Upload a page rendition",
            description = "Multipart upload containing file and JSON metadata. Identical retries are idempotent."
    )
    @PostMapping(
            value = "/{jobId}/pages/{pageId}/renditions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<WorkerPageRenditionDetails> uploadRendition(@PathVariable
                                                                      Long jobId,
                                                                      @PathVariable
                                                                      Long pageId,
                                                                      @Parameter(
                                                                              description = "Worker identifier used when the job was claimed.",
                                                                              example = "page-extractor-1",
                                                                              required = true
                                                                      )
                                                                      @RequestHeader("X-Worker-Id")
                                                                      String workerId,
                                                                      @Parameter(
                                                                              description = "Opaque token valid only for this job and processing attempt.",
                                                                              required = true,
                                                                              schema = @Schema(type = "string", format = "password")
                                                                      )
                                                                      @RequestHeader("X-Worker-Claim-Token")
                                                                      String claimToken,
                                                                      @Parameter(
                                                                              description = "Rendered page image. The server validates MIME type, size and content.",
                                                                              required = true,
                                                                              content = @Content(
                                                                                      mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                                                                      schema = @Schema(type = "string", format = "binary")
                                                                              )
                                                                      )
                                                                      @RequestPart("file")
                                                                      MultipartFile file,
                                                                      @Parameter(
                                                                              description = "JSON metadata describing the rendition.",
                                                                              required = true,
                                                                              content = @Content(
                                                                                      mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                                      schema = @Schema(implementation = WorkerPageRenditionCommand.class)
                                                                              )
                                                                      )
                                                                      @Valid @RequestPart("metadata")
                                                                      WorkerPageRenditionCommand command
    ) {
        WorkerPageRenditionDetails details = renditionService.upload(
                jobId,
                pageId,
                new WorkerClaimCredentials(workerId, claimToken),
                command,
                file
        );

        return ResponseEntity
                .status(details.existing() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(details);
    }
    //---------------------------------------------------------------------------------------
    //endregion


    //region complete
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Complete a claimed job",
            description = "Validates required outputs before marking the job successful."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job completed"),
            @ApiResponse(responseCode = "409", description = "Stale claim or invalid job state"),
            @ApiResponse(responseCode = "410", description = "Claim expired"),
            @ApiResponse(responseCode = "422", description = "Required outputs are incomplete")
    })
    @PostMapping("/{jobId}/complete")
    public WorkerJobCompletionDetails complete(@PathVariable
                                               Long jobId,
                                               @Parameter(
                                                       description = "Worker identifier used when the job was claimed.",
                                                       example = "page-extractor-1",
                                                       required = true
                                               )
                                               @RequestHeader("X-Worker-Id")
                                               String workerId,
                                               @Parameter(
                                                       description = "Opaque token valid only for this job and processing attempt.",
                                                       required = true,
                                                       schema = @Schema(type = "string", format = "password")
                                               )
                                               @RequestHeader("X-Worker-Claim-Token")
                                               String claimToken
    ) {
        return completionService.complete(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken)
        );
    }
    //---------------------------------------------------------------------------------------
    //endregion


    //region fail
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Report job failure",
            description = "Moves the job to retry wait or a terminal failure state."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job state updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "409", description = "Stale claim or invalid job state"),
            @ApiResponse(responseCode = "410", description = "Claim expired")
    })
    @PostMapping("/{jobId}/fail")
    public WorkerJobFailureDetails fail(@PathVariable
                                        Long jobId,
                                        @Parameter(
                                                description = "Worker identifier used when the job was claimed.",
                                                example = "page-extractor-1",
                                                required = true
                                        )
                                        @RequestHeader("X-Worker-Id")
                                        String workerId,
                                        @Parameter(
                                                description = "Opaque token valid only for this job and processing attempt.",
                                                required = true,
                                                schema = @Schema(type = "string", format = "password")
                                        )
                                        @RequestHeader("X-Worker-Claim-Token")
                                        String claimToken,
                                        @Valid @RequestBody
                                        WorkerJobFailureCommand command
    ) {
        return failureService.fail(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken),
                command
        );
    }
    //---------------------------------------------------------------------------------------
    //endregion


    //region cancelled
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Acknowledge cancellation",
            description = "Marks a cancellation-requested job as cancelled."
    )
    @PostMapping("/{jobId}/cancelled")
    public WorkerJobCancellationDetails cancelled(@PathVariable
                                                  Long jobId,
                                                  @Parameter(
                                                          description = "Worker identifier used when the job was claimed.",
                                                          example = "page-extractor-1",
                                                          required = true
                                                  )
                                                  @RequestHeader("X-Worker-Id")
                                                  String workerId,
                                                  @Parameter(
                                                          description = "Opaque token valid only for this job and processing attempt.",
                                                          required = true,
                                                          schema = @Schema(type = "string", format = "password")
                                                  )
                                                  @RequestHeader("X-Worker-Claim-Token")
                                                  String claimToken
    ) {
        return cancellationService.acknowledge(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken)
        );
    }
    //---------------------------------------------------------------------------------------
    //endregion


    private MediaType contentType(String mimeType) {
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}