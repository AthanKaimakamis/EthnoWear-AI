package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.command.ocr.ManualOcrImportCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageOcrResultDetails;
import fmi.ethnowear.application.service.document.ocr.DocumentPageManualOcrImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/document-pages/{pageId}/ocr-results")
@RequiredArgsConstructor
public class AdminDocumentPageOcrController {

    private final DocumentPageManualOcrImportService manualOcrImportService;

    @PostMapping
    public ResponseEntity<DocumentPageOcrResultDetails> importManualResult(
            @PathVariable Long pageId,
            @Valid @RequestBody ManualOcrImportCommand command
    ) {
        DocumentPageOcrResultDetails result = manualOcrImportService.importResult(
                pageId,
                command
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result);
    }
}