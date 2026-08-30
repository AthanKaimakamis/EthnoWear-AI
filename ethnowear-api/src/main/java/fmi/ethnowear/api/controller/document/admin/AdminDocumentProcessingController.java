package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingRequestService;
import fmi.ethnowear.application.dto.document.command.retention.MediaCleanupScheduleCommand;
import fmi.ethnowear.application.dto.document.query.retention.MediaCleanupEligibilityDetails;
import fmi.ethnowear.application.dto.document.query.retention.MediaCleanupScheduleDetails;
import fmi.ethnowear.application.dto.document.query.chunk.ChunkGenerationEligibilityDetails;
import fmi.ethnowear.application.dto.document.query.chunk.GeneratedKnowledgeChunkDetails;
import fmi.ethnowear.application.service.document.chunk.DocumentChunkGenerationEligibilityService;
import fmi.ethnowear.application.service.document.chunk.DocumentChunkQueryService;
import fmi.ethnowear.application.service.document.retention.MediaCleanupEligibilityService;
import fmi.ethnowear.application.service.document.retention.MediaCleanupSchedulingService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDocumentProcessingController {

    private final DocumentProcessingRequestService processingRequestService;
    private final MediaCleanupEligibilityService cleanupEligibilityService;
    private final MediaCleanupSchedulingService cleanupSchedulingService;
    private final DocumentChunkGenerationEligibilityService chunkEligibility;
    private final DocumentChunkQueryService chunkQueryService;

    @PostMapping("/document-pages/{pageId}/ocr")
    public ResponseEntity<DocumentProcessingJobDetails> requestOcr(
            @PathVariable Long pageId
    ) {
        return accepted(processingRequestService.requestOcr(pageId));
    }

    @PostMapping("/document-pages/{pageId}/reprocess")
    public ResponseEntity<DocumentProcessingJobDetails> requestReprocessing(
            @PathVariable Long pageId
    ) {
        return accepted(processingRequestService.requestReprocessing(pageId));
    }

    @Operation(
            summary = "Create a replacement OCR job",
            description = "Creates a new OCR job linked to the page's previous OCR job. Every OCR reprocessing attempt also creates a new job ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Replacement OCR job accepted"),
            @ApiResponse(responseCode = "404", description = "Document page not found"),
            @ApiResponse(responseCode = "409", description = "Another OCR job for the page is active")
    })
    @PostMapping("/document-pages/{pageId}/ocr-jobs")
    public ResponseEntity<DocumentProcessingJobDetails> createOcrJob(
            @PathVariable Long pageId
    ) {
        return accepted(processingRequestService.createOcrJob(pageId));
    }

    @PostMapping("/document-pages/{pageId}/quality-assessment")
    public ResponseEntity<DocumentProcessingJobDetails> requestQualityAssessment(
            @PathVariable Long pageId
    ) {
        return accepted(processingRequestService.requestQualityAssessment(pageId));
    }

    @Operation(
            summary = "Queue vision-assisted OCR review",
            description = "Queues an advisory vision comparison for the current OCR result. It never changes or approves page text."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Manual vision review accepted"),
            @ApiResponse(responseCode = "404", description = "Document page not found"),
            @ApiResponse(
                    responseCode = "409",
                    description = "VISION_JOB_ALREADY_ACTIVE with the safe active-job summary"
            )
    })
    @PostMapping("/document-pages/{pageId}/vision-assessment")
    public ResponseEntity<DocumentProcessingJobDetails> requestVisionAssessment(
            @PathVariable Long pageId
    ) {
        return accepted(processingRequestService.requestVisionAssessment(pageId));
    }

    @PostMapping("/documents/{documentId}/chunk-generation")
    public ResponseEntity<DocumentProcessingJobDetails> requestChunkGeneration(
            @PathVariable Long documentId
    ) {
        return accepted(processingRequestService.requestChunkGeneration(documentId));
    }

    @GetMapping("/documents/{documentId}/chunk-generation/eligibility")
    public ChunkGenerationEligibilityDetails chunkGenerationEligibility(
            @PathVariable Long documentId
    ) {
        return chunkEligibility.eligibility(documentId);
    }

    @GetMapping("/documents/{documentId}/chunk-generation/jobs")
    public Page<DocumentProcessingJobDetails> chunkGenerationJobs(
            @PathVariable Long documentId,
            Pageable pageable
    ) {
        return chunkQueryService.findGenerationJobs(documentId, pageable);
    }

    @GetMapping("/documents/{documentId}/generated-chunks")
    public Page<GeneratedKnowledgeChunkDetails> generatedChunks(
            @PathVariable Long documentId,
            Pageable pageable
    ) {
        return chunkQueryService.findGeneratedChunks(documentId, pageable);
    }

    @Operation(
            summary = "Check document media-cleanup eligibility",
            description = "Checks processing, approval, chunk indexing, active jobs, and generated-media availability without deleting content."
    )
    @GetMapping("/documents/{documentId}/media-cleanup/eligibility")
    public MediaCleanupEligibilityDetails mediaCleanupEligibility(
            @PathVariable Long documentId
    ) {
        return cleanupEligibilityService.evaluate(documentId);
    }

    @Operation(
            summary = "Schedule generated document-media cleanup",
            description = "Creates a durable MEDIA_CLEANUP job. Original PDFs and uploaded or replacement scans are always preserved."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Cleanup scheduled after the retention period"),
            @ApiResponse(responseCode = "404", description = "Document not found"),
            @ApiResponse(responseCode = "409", description = "Document is not eligible or cleanup is already active")
    })
    @PostMapping("/documents/{documentId}/media-cleanup")
    public ResponseEntity<MediaCleanupScheduleDetails> scheduleMediaCleanup(
            @PathVariable Long documentId,
            @Valid @RequestBody(required = false)
            MediaCleanupScheduleCommand command
    ) {
        return ResponseEntity.accepted().body(
                cleanupSchedulingService.schedule(documentId, command)
        );
    }

    private @NonNull ResponseEntity<DocumentProcessingJobDetails> accepted(
            DocumentProcessingJobDetails job
    ) {
        return ResponseEntity.accepted().body(job);
    }
}
