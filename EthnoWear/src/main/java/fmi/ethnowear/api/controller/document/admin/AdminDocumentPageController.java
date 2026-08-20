package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.query.DocumentPageDetails;
import fmi.ethnowear.application.dto.document.query.DocumentPageSummaryDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageOcrResultDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageProvenanceEventDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageReviewDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.dto.document.query.quality.DocumentPageQualityAssessmentDetails;
import fmi.ethnowear.application.service.document.query.DocumentHistoryQueryService;
import fmi.ethnowear.application.service.document.query.DocumentPageQueryService;
import fmi.ethnowear.application.service.document.query.DocumentQualityQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@RestController
@RequestMapping("/api/admin/documents/{documentId}/pages")
@RequiredArgsConstructor
public class AdminDocumentPageController {

    private final DocumentPageQueryService pageQueryService;
    private final DocumentHistoryQueryService historyQueryService;
    private final DocumentQualityQueryService qualityQueryService;

    @GetMapping
    public Page<DocumentPageSummaryDetails> findAll(
            @PathVariable Long documentId,
            Pageable pageable) {
        return pageQueryService.findByDocumentId(
                documentId,
                pageable
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
