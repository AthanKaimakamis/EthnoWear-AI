package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.query.DocumentPageDetails;
import fmi.ethnowear.application.dto.document.query.DocumentPageSummaryDetails;
import fmi.ethnowear.application.dto.document.query.DocumentPageQueryDto;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageOcrResultDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageProvenanceEventDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageReviewDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.dto.document.query.quality.DocumentPageQualityAssessmentDetails;
import fmi.ethnowear.application.service.document.query.DocumentHistoryQueryService;
import fmi.ethnowear.application.service.document.query.DocumentPageQueryService;
import fmi.ethnowear.application.service.document.query.DocumentQualityQueryService;
import fmi.ethnowear.application.service.document.query.DocumentPageWorkflowQueryService;
import fmi.ethnowear.application.service.document.processing.DocumentWorkflowManagementService;
import fmi.ethnowear.application.dto.document.query.workflow.DocumentPageWorkflowProgressDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/documents/{documentId}/pages")
@RequiredArgsConstructor
public class AdminDocumentPageController {

    private final DocumentPageQueryService pageQueryService;
    private final DocumentHistoryQueryService historyQueryService;
    private final DocumentQualityQueryService qualityQueryService;
    private final DocumentPageWorkflowQueryService workflowQueryService;
    private final DocumentWorkflowManagementService workflowManagementService;

    @GetMapping
    public Page<DocumentPageSummaryDetails> findAll(
            @PathVariable Long documentId,
            @ParameterObject @ModelAttribute DocumentPageQueryDto query,
            @PageableDefault(sort = "pageSequence", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return pageQueryService.findByDocumentId(
                documentId,
                query,
                pageable
        );
    }

    @GetMapping("/{pageId}/workflow")
    @Operation(summary = "Get authoritative page workflow progress")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workflow returned"),
            @ApiResponse(responseCode = "404", description = "Page not found")
    })
    public DocumentPageWorkflowProgressDetails workflow(
            @PathVariable Long documentId,
            @PathVariable Long pageId
    ) {
        return workflowQueryService.progress(documentId, pageId);
    }

    @Operation(summary = "Start image extraction for one document page")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Extraction queued"),
            @ApiResponse(responseCode = "400", description = "Page cannot be extracted"),
            @ApiResponse(responseCode = "404", description = "Page not found"),
            @ApiResponse(responseCode = "409", description = "Extraction is already active")
    })
    @PostMapping("/{pageId}/image-extraction")
    public ResponseEntity<DocumentProcessingJobDetails> startImageExtraction(
            @PathVariable Long documentId,
            @PathVariable Long pageId
    ) {
        return ResponseEntity.accepted().body(
                workflowManagementService.startPageImageExtraction(
                        documentId,
                        pageId
                )
        );
    }

    @Operation(
            summary = "Regenerate a document page image",
            description = "Queues page extraction from the preserved original PDF. Manual and replacement scans are never regenerated or replaced."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Regeneration queued"),
            @ApiResponse(responseCode = "400", description = "Page is not backed by an original PDF"),
            @ApiResponse(responseCode = "404", description = "Page not found"),
            @ApiResponse(responseCode = "409", description = "Page extraction is already active")
    })
    @PostMapping("/{pageId}/regenerate-media")
    public ResponseEntity<DocumentProcessingJobDetails> regenerateMedia(
            @PathVariable Long documentId,
            @PathVariable Long pageId
    ) {
        return ResponseEntity.accepted().body(
                workflowManagementService.startPageImageExtraction(
                        documentId,
                        pageId
                )
        );
    }

    @Operation(summary = "Safely retire a document page")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page retired"),
            @ApiResponse(responseCode = "400", description = "Invalid retirement request"),
            @ApiResponse(responseCode = "404", description = "Page not found"),
            @ApiResponse(responseCode = "409", description = "Page has dependencies or is stale")
    })
    @DeleteMapping("/{pageId}")
    public DocumentPageDetails retire(
            @PathVariable Long documentId,
            @PathVariable Long pageId,
            @RequestHeader("If-Match") String versionToken,
            @RequestParam String reason,
            Principal principal
    ) {
        return workflowManagementService.retirePage(
                documentId,
                pageId,
                versionToken,
                reason,
                principal.getName()
        );
    }

    @GetMapping("/{pageId}")
    public DocumentPageDetails findById(
            @PathVariable Long documentId,
            @PathVariable Long pageId
    ) {
        return pageQueryService.findById(
                documentId,
                pageId
        );
    }

    @GetMapping("/{pageId}/ocr/current")
    public ResponseEntity<DocumentPageOcrResultDetails> findCurrentOcrResult(
            @PathVariable Long documentId,
            @PathVariable Long pageId
    ) {
        return ResponseEntity.of(historyQueryService.findCurrentOcrResult(
                documentId,
                pageId
        ));
    }

    @GetMapping("/{pageId}/ocr/history")
    public Page<DocumentPageOcrResultDetails> findOcrHistory(
            @PathVariable Long documentId,
            @PathVariable Long pageId,
            Pageable pageable
    ) {
        return historyQueryService.findOcrHistory(
                documentId,
                pageId,
                pageable
        );
    }

    @GetMapping("/{pageId}/reviews")
    public Page<DocumentPageReviewDetails> findReviewHistory(
            @PathVariable Long documentId,
            @PathVariable Long pageId,
            Pageable pageable
    ) {
        return historyQueryService.findReviewHistory(
                documentId,
                pageId,
                pageable);
    }

    @GetMapping("/{pageId}/provenance")
    public Page<DocumentPageProvenanceEventDetails> findProvenanceHistory(
            @PathVariable Long documentId,
            @PathVariable Long pageId,
            Pageable pageable
    ) {
        return historyQueryService.findProvenanceHistory(
                documentId,
                pageId,
                pageable
        );
    }

    @GetMapping("/{pageId}/jobs")
    public Page<DocumentProcessingJobDetails> findJobHistory(
            @PathVariable Long documentId,
            @PathVariable Long pageId,
            Pageable pageable
    ) {
        return historyQueryService.findPageJobs(
                documentId,
                pageId,
                pageable
        );
    }

    @GetMapping("/{pageId}/quality/current")
    public List<DocumentPageQualityAssessmentDetails> findCurrentQuality(
            @PathVariable Long documentId,
            @PathVariable Long pageId
    ) {
        return qualityQueryService.findCurrentAssessments(
                documentId,
                pageId
        );
    }

    @GetMapping("/{pageId}/quality/history")
    public Page<DocumentPageQualityAssessmentDetails> findQualityHistory(
            @PathVariable Long documentId,
            @PathVariable Long pageId,
            Pageable pageable
    ) {
        return qualityQueryService.findAssessmentHistory(
                documentId,
                pageId,
                pageable
        );
    }
}
