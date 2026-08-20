package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingRequestService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDocumentProcessingController {

    private final DocumentProcessingRequestService processingRequestService;

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

    @PostMapping("/document-pages/{pageId}/quality-assessment")
    public ResponseEntity<DocumentProcessingJobDetails> requestQualityAssessment(
            @PathVariable Long pageId
    ) {
        return accepted(processingRequestService.requestQualityAssessment(pageId));
    }

    @PostMapping("/documents/{documentId}/chunk-generation")
    public ResponseEntity<DocumentProcessingJobDetails> requestChunkGeneration(
            @PathVariable Long documentId
    ) {
        return accepted(processingRequestService.requestChunkGeneration(documentId));
    }

    private @NonNull ResponseEntity<DocumentProcessingJobDetails> accepted(
            DocumentProcessingJobDetails job
    ) {
        return ResponseEntity.accepted().body(job);
    }
}