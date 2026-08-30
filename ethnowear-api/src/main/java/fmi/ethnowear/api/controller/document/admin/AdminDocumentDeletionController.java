package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.service.document.lifecycle.DocumentDeletionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/documents")
@RequiredArgsConstructor
public class AdminDocumentDeletionController {

    private final DocumentDeletionService deletionService;

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<Void> delete(
            @PathVariable Long documentId,
            @RequestParam(defaultValue = "false") boolean confirm
    ) {
        deletionService.delete(documentId, confirm);

        return ResponseEntity.noContent().build();
    }
}
