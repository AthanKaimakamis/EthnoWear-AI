package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.command.metadata.DocumentPageMetadataUpdateCommand;
import fmi.ethnowear.application.service.document.metadata.DocumentPageMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/documents/{documentId}/pages/{pageId}/metadata")
@RequiredArgsConstructor
public class AdminDocumentPageMetadataController {

    private final DocumentPageMetadataService metadataService;

    @PatchMapping
    public ResponseEntity<Void> update(
            @PathVariable Long documentId,
            @PathVariable Long pageId,
            @RequestHeader("If-Match") String versionToken,
            @Valid @RequestBody DocumentPageMetadataUpdateCommand command
    ) {
        metadataService.update(
                documentId,
                pageId,
                versionToken,
                command
        );

        return ResponseEntity.noContent().build();
    }
}
