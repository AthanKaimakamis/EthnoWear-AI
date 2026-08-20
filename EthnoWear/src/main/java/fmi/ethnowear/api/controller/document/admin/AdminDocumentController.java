package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.query.*;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.service.document.query.DocumentHistoryQueryService;
import fmi.ethnowear.application.service.document.query.DocumentIndexingQueryService;
import fmi.ethnowear.application.service.document.query.DocumentProgressQueryService;
import fmi.ethnowear.application.service.document.query.DocumentQueryService;
import fmi.ethnowear.application.dto.document.command.metadata.DocumentMetadataUpdateCommand;
import fmi.ethnowear.application.service.document.metadata.DocumentMetadataService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/documents")
@RequiredArgsConstructor
public class AdminDocumentController {

    private final DocumentQueryService documentQueryService;
    private final DocumentProgressQueryService progressQueryService;
    private final DocumentIndexingQueryService indexingQueryService;
    private final DocumentHistoryQueryService historyQueryService;
    private final DocumentMetadataService metadataService;

    @GetMapping
    public Page<DocumentSummaryDetails> findAll(@ModelAttribute DocumentQueryDto query,
                                                Pageable pageable) {
        return documentQueryService.findAll(query, pageable);
    }

    @GetMapping("/{documentId}")
    public DocumentDetails findById(@PathVariable Long documentId) {
        return documentQueryService.findById(documentId);
    }

    @GetMapping("/{documentId}/progress")
    public DocumentProgressDetails findProgress(@PathVariable Long documentId) {
        return progressQueryService.findByDocumentId(documentId);
    }

    @GetMapping("/{documentId}/indexing-status")
    public DocumentIndexingStatusDetails findIndexingStatus(@PathVariable Long documentId) {
        return indexingQueryService.findByDocumentId(documentId);
    }

    @GetMapping("/{documentId}/jobs")
    public Page<DocumentProcessingJobDetails> findDocumentJobs(@PathVariable Long documentId,
                                                               Pageable pageable) {
        return historyQueryService.findDocumentJobs(documentId, pageable);
    }

    @PutMapping("/{documentId}/metadata")
    public ResponseEntity<Void> updateMetadata(@PathVariable Long documentId,
                                               @Valid @RequestBody
                                               DocumentMetadataUpdateCommand command) {
        metadataService.update(documentId, command);

        return ResponseEntity.noContent().build();
    }

}
