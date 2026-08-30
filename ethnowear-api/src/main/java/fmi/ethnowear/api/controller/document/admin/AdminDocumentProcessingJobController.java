package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.command.processing.DocumentJobCancellationCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/document-processing-jobs/{jobId}")
@RequiredArgsConstructor
public class AdminDocumentProcessingJobController {

    private final DocumentProcessingJobLifecycleService jobLifecycleService;

    @PostMapping("/retry")
    public ResponseEntity<DocumentProcessingJobDetails> retry(
            @PathVariable Long jobId
    ) {
        return ResponseEntity
                .accepted()
                .body(jobLifecycleService.retry(jobId));
    }

    @PostMapping("/cancel")
    public DocumentProcessingJobDetails cancel(
            @PathVariable Long jobId,
            @Valid @RequestBody DocumentJobCancellationCommand command
    ) {
        return jobLifecycleService.cancel(jobId, command);
    }
}