package fmi.ethnowear.api.controller.worker.internal;

import fmi.ethnowear.application.dto.worker.cancellation.WorkerJobCancellationDetails;
import fmi.ethnowear.application.dto.worker.completion.WorkerJobCompletionDetails;
import fmi.ethnowear.application.dto.worker.extraction.*;
import fmi.ethnowear.application.dto.worker.failure.*;
import fmi.ethnowear.application.dto.worker.figure.*;
import fmi.ethnowear.application.dto.worker.heartbeat.*;
import fmi.ethnowear.application.dto.worker.job.*;
import fmi.ethnowear.application.dto.worker.indexing.*;
import fmi.ethnowear.application.dto.worker.ocr.*;
import fmi.ethnowear.application.dto.worker.quality.*;
import fmi.ethnowear.application.dto.worker.rendition.*;
import fmi.ethnowear.application.dto.worker.vision.*;
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
    private final WorkerOcrResultService ocrResultService;
    private final WorkerQualityAssessmentService qualityAssessmentService;
    private final WorkerVisionOcrAssessmentService visionAssessmentService;
    private final WorkerFigureExtractionService figureExtractionService;
    private final WorkerIndexingService indexingService;
    private final WorkerEmbeddingService embeddingService;
    private final WorkerJobFailureService failureService;
    private final WorkerJobCancellationService cancellationService;

    //region claim
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Claim the next available job",
            description = """
                    Atomically claims one supported queued job. The requested lease must be
                    within the configured minimum and maximum. The returned claim token is
                    valid only for the claimed job and attempt. Every successful response
                    guarantees leaseExpiresAt > claimedAt and
                    heartbeatIntervalSeconds < maximumLeaseSeconds.
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

    //region figureExtraction
    @Operation(
            summary = "Get page-figure extraction context",
            description = "Returns the exact current page rendition, OCR layout and bounded persisted candidates for the claimed job."
    )
    @GetMapping("/{jobId}/figure-extraction/context")
    public WorkerFigureExtractionContextDetails figureExtractionContext(
            @PathVariable Long jobId,
            @RequestHeader("X-Worker-Id") String workerId,
            @RequestHeader("X-Worker-Claim-Token") String claimToken
    ) {
        return figureExtractionService.context(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken)
        );
    }

    @Operation(
            summary = "Upload a confirmed page figure",
            description = "Stores a validated crop through guarded media storage and links it to the exact page, rendition, OCR candidate, job and attempt. Identical retries are idempotent."
    )
    @PostMapping(
            value = "/{jobId}/figures",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<WorkerFigureCropDetails> uploadFigure(
            @PathVariable Long jobId,
            @RequestHeader("X-Worker-Id") String workerId,
            @RequestHeader("X-Worker-Claim-Token") String claimToken,
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") WorkerFigureCropCommand command
    ) {
        WorkerFigureCropDetails details = figureExtractionService.upload(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken),
                command,
                file
        );

        return ResponseEntity
                .status(details.existing() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(details);
    }
    //endregion


    //region heartbeat
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Renew a job lease",
            description = """
                    Renews the active lease without exceeding the job timeout. Workers should
                    heartbeat at the interval returned in the claim resource limits and stop
                    processing when cancellationRequested is true. Every successful heartbeat
                    returns a lease expiry in the future. A 409 means ownership is stale and a
                    410 means the claim expired; in both cases the worker must stop processing.
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
            description = """
                    Returns controlled input without exposing storage paths. PAGE_EXTRACTION
                    streams the source PDF; OCR streams the claimed page's preferred image
                    rendition. OCR_QUALITY_ASSESSMENT and VISION_OCR_ASSESSMENT stream the
                    exact page rendition bound to the claimed job. EXTRACT_PAGE_FIGURES
                    streams the exact rendition associated with its current OCR result.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF or page-image content"),
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


    //region visionAssessment
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Get vision OCR-assessment context",
            description = """
                    Returns the current OCR text, deterministic quality assessment and bounded
                    image metadata for the exact page rendition bound to the claimed job.
                    Page, OCR-result and media identities are derived by Spring.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vision-assessment context returned"),
            @ApiResponse(responseCode = "404", description = "Current OCR result or input not found"),
            @ApiResponse(responseCode = "409", description = "Stale claim, stale OCR result or cancellation"),
            @ApiResponse(responseCode = "410", description = "Claim expired"),
            @ApiResponse(responseCode = "413", description = "Context exceeds configured limits"),
            @ApiResponse(responseCode = "422", description = "OCR, deterministic assessment or image is inconsistent")
    })
    @GetMapping("/{jobId}/vision-assessment/context")
    public WorkerVisionAssessmentContextDetails visionAssessmentContext(
            @PathVariable Long jobId,
            @RequestHeader("X-Worker-Id") String workerId,
            @RequestHeader("X-Worker-Claim-Token") String claimToken
    ) {
        return visionAssessmentService.context(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken)
        );
    }

    @Operation(
            summary = "Submit a vision OCR assessment and text suggestion",
            description = """
                    Persists an advisory vision comparison and immutable text suggestion for
                    the claimed OCR result, including bounded issues, confidence values,
                    uncertain passages and the explicit review requirement. It never stores
                    the raw model response or changes raw OCR, corrected text, review state
                    or approval. Identical retries compare the complete structured result;
                    conflicting retries return 409. The assessment becomes current only after
                    job completion.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Identical result already accepted"),
            @ApiResponse(responseCode = "201", description = "Vision result accepted"),
            @ApiResponse(responseCode = "400", description = "Vision result validation failed"),
            @ApiResponse(responseCode = "409", description = "Conflicting result, stale claim or cancellation"),
            @ApiResponse(responseCode = "410", description = "Claim expired"),
            @ApiResponse(responseCode = "413", description = "Result exceeds configured limits"),
            @ApiResponse(responseCode = "422", description = "Claimed evidence is inconsistent")
    })
    @PostMapping("/{jobId}/vision-assessment")
    public ResponseEntity<WorkerVisionAssessmentDetails> visionAssessment(
            @PathVariable Long jobId,
            @RequestHeader("X-Worker-Id") String workerId,
            @RequestHeader("X-Worker-Claim-Token") String claimToken,
            @Valid @RequestBody WorkerVisionAssessmentCommand command
    ) {
        WorkerVisionAssessmentDetails details = visionAssessmentService.accept(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken),
                command
        );

        return ResponseEntity
                .status(details.existing() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(details);
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
            description = """
                    Multipart upload containing file and JSON metadata. Identical retries are
                    idempotent while the same claim remains valid. If a later heartbeat returns
                    409 or 410, an already accepted rendition remains accepted, but the worker
                    must not retry uploads using the stale or expired claim.
                    """
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
            description = """
                    Validates required outputs before marking the job successful. OCR completion
                    requires an accepted OCR result. OCR quality-assessment completion requires
                    an accepted assessment, makes it current, preserves history, and leaves the
                    page pending human review. Vision OCR-assessment completion requires an
                    accepted advisory assessment and immutable suggestion but never changes or
                    approves page text. INDEX_CHUNK completion requires an accepted index
                    result, revalidates the current content hash, and atomically promotes the
                    accepted vector metadata to the chunk. The vector point ID is always the
                    knowledge chunk ID. If Qdrant accepted the point before Spring accepted the
                    result, the worker must safely upsert the same point ID and retry. Identical
                    completion retries return the existing result. A 409 means the claim is stale
                    or the job state conflicts; a 410 means the lease expired. The worker must
                    stop on either response.
                    """
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


    //region qualityAssessment
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Get OCR quality-assessment context",
            description = """
                    Returns the claimed page's current OCR result and bounded image metadata.
                    Storage keys and filesystem paths are never exposed. A stale claim returns
                    409, an expired lease returns 410, and inconsistent evidence returns 422.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quality-assessment context returned"),
            @ApiResponse(responseCode = "404", description = "Current OCR result or input not found"),
            @ApiResponse(responseCode = "409", description = "Stale claim or cancellation requested"),
            @ApiResponse(responseCode = "410", description = "Claim expired"),
            @ApiResponse(responseCode = "413", description = "Context exceeds configured limits"),
            @ApiResponse(responseCode = "422", description = "OCR result or image is inconsistent")
    })
    @GetMapping("/{jobId}/quality-assessment/context")
    public WorkerQualityAssessmentContextDetails qualityAssessmentContext(
            @PathVariable Long jobId,
            @RequestHeader("X-Worker-Id") String workerId,
            @RequestHeader("X-Worker-Claim-Token") String claimToken
    ) {
        return qualityAssessmentService.context(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken)
        );
    }

    @Operation(
            summary = "Submit an OCR quality assessment",
            description = """
                    Accepts one assessment for the claimed job. Job, page, OCR-result and input
                    identities are derived by Spring. PASS, WARNING and FAIL map to HIGH_QUALITY,
                    MINOR_REVIEW and POOR_QUALITY. Identical retries return 200; a different retry
                    returns 409. The assessment becomes current only after completion. On 409 or
                    410 the worker must stop; on 5xx it may retry with bounded backoff while the
                    lease remains valid.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Identical assessment already accepted"),
            @ApiResponse(responseCode = "201", description = "Assessment accepted"),
            @ApiResponse(responseCode = "400", description = "Assessment validation failed"),
            @ApiResponse(responseCode = "409", description = "Conflicting assessment, stale claim or cancellation"),
            @ApiResponse(responseCode = "410", description = "Claim expired"),
            @ApiResponse(responseCode = "413", description = "Assessment exceeds configured limits"),
            @ApiResponse(responseCode = "422", description = "OCR result or image is inconsistent")
    })
    @PostMapping("/{jobId}/quality-assessment")
    public ResponseEntity<WorkerQualityAssessmentDetails> qualityAssessment(
            @PathVariable Long jobId,
            @RequestHeader("X-Worker-Id") String workerId,
            @RequestHeader("X-Worker-Claim-Token") String claimToken,
            @Valid @RequestBody WorkerQualityAssessmentCommand command
    ) {
        WorkerQualityAssessmentDetails details = qualityAssessmentService.accept(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken),
                command
        );

        return ResponseEntity
                .status(details.existing() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(details);
    }
    //---------------------------------------------------------------------------------------
    //endregion


    //region ocrResult
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Submit an OCR result",
            description = """
                    Accepts one result for the claimed OCR job. Page, input media and
                    processing-job identities are derived from the claim. Identical retries
                    return the existing result; conflicting retries return 409. The result
                    becomes current only after successful job completion.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Identical result already accepted"),
            @ApiResponse(responseCode = "201", description = "OCR result accepted"),
            @ApiResponse(responseCode = "409", description = "Conflicting result or stale claim"),
            @ApiResponse(responseCode = "410", description = "Claim expired"),
            @ApiResponse(responseCode = "422", description = "OCR input is inconsistent")
    })
    @PostMapping("/{jobId}/ocr-result")
    public ResponseEntity<WorkerOcrResultDetails> ocrResult(
            @PathVariable Long jobId,
            @RequestHeader("X-Worker-Id") String workerId,
            @RequestHeader("X-Worker-Claim-Token") String claimToken,
            @Valid @RequestBody WorkerOcrResultCommand command
    ) {
        WorkerOcrResultDetails details = ocrResultService.accept(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken),
                command
        );

        return ResponseEntity
                .status(details.existing() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(details);
    }
    //---------------------------------------------------------------------------------------
    //endregion


    //region indexing
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Get authoritative knowledge-chunk indexing context",
            description = """
                    Returns bounded SQL-authoritative text for the knowledge chunk targeted by
                    the claimed INDEX_CHUNK job. The server validates approval, provenance,
                    content hash, supersession and every associated page. Chunk identity is
                    derived from the claim and is never accepted from the worker.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Indexing context returned"),
            @ApiResponse(responseCode = "409", description = "Stale claim, cancellation or inconsistent target"),
            @ApiResponse(responseCode = "410", description = "Claim expired"),
            @ApiResponse(responseCode = "413", description = "Chunk content exceeds configured limits"),
            @ApiResponse(responseCode = "422", description = "Chunk or cited pages are not eligible")
    })
    @GetMapping("/{jobId}/indexing-context")
    public WorkerIndexingContextDetails indexingContext(
            @PathVariable Long jobId,
            @RequestHeader("X-Worker-Id") String workerId,
            @RequestHeader("X-Worker-Claim-Token") String claimToken
    ) {
        return indexingService.context(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken)
        );
    }

    @Operation(
            summary = "Generate the claimed knowledge chunk embedding",
            description = """
                    Generates the embedding from SQL-authoritative content targeted by the
                    claimed INDEX_CHUNK job. The worker cannot submit text, model, dimensions
                    or chunk identity. The claim and chunk are revalidated before the vector
                    is returned.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Embedding generated"),
            @ApiResponse(responseCode = "409", description = "Stale claim or changed chunk"),
            @ApiResponse(responseCode = "410", description = "Claim expired"),
            @ApiResponse(responseCode = "422", description = "Chunk is not eligible"),
            @ApiResponse(responseCode = "503", description = "Embedding runtime unavailable")
    })
    @GetMapping("/{jobId}/embedding")
    public WorkerEmbeddingDetails embedding(
            @PathVariable Long jobId,
            @RequestHeader("X-Worker-Id") String workerId,
            @RequestHeader("X-Worker-Claim-Token") String claimToken
    ) {
        return embeddingService.generate(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken)
        );
    }

    @Operation(
            summary = "Accept knowledge-chunk vector metadata",
            description = """
                    Accepts metadata only after the worker upserts the deterministic point in
                    Qdrant. The point ID must equal the SQL KnowledgeChunk ID. Embedding vectors
                    are never submitted to Spring. Identical retries are idempotent; conflicting
                    retries return 409. A stale content hash is rejected.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Identical result already accepted"),
            @ApiResponse(responseCode = "201", description = "Index result accepted"),
            @ApiResponse(responseCode = "400", description = "Result violates configured limits or embedding contract"),
            @ApiResponse(responseCode = "409", description = "Conflicting result, stale hash, stale claim or cancellation"),
            @ApiResponse(responseCode = "410", description = "Claim expired"),
            @ApiResponse(responseCode = "422", description = "Chunk or cited pages are not eligible")
    })
    @PostMapping("/{jobId}/index-result")
    public ResponseEntity<WorkerIndexResultDetails> indexResult(
            @PathVariable Long jobId,
            @RequestHeader("X-Worker-Id") String workerId,
            @RequestHeader("X-Worker-Claim-Token") String claimToken,
            @Valid @RequestBody WorkerIndexResultCommand command
    ) {
        WorkerIndexResultDetails details = indexingService.accept(
                jobId,
                new WorkerClaimCredentials(workerId, claimToken),
                command
        );

        return ResponseEntity
                .status(details.existing() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(details);
    }
    //---------------------------------------------------------------------------------------
    //endregion


    //region fail
    //---------------------------------------------------------------------------------------
    @Operation(
            summary = "Report job failure",
            description = """
                    Moves the job to retry wait or a terminal failure state. On 409 or 410 the
                    worker must stop because it no longer owns a valid claim. On 5xx the worker
                    may retry reporting with bounded backoff while its claim remains valid; if
                    reporting cannot be completed, it must stop and allow lease recovery.
                    """
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
