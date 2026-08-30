package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.command.provenance.DocumentDefaultSourceReferenceCommand;
import fmi.ethnowear.application.dto.document.query.DocumentSourceInheritanceDetails;
import fmi.ethnowear.application.service.document.provenance.DocumentSourceReferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/documents/{documentId}/default-source-reference")
@RequiredArgsConstructor
public class AdminDocumentSourceReferenceController {

    private final DocumentSourceReferenceService sourceReferenceService;

    @PutMapping
    public DocumentSourceInheritanceDetails setDefault(
            @PathVariable Long documentId,
            @Valid @RequestBody DocumentDefaultSourceReferenceCommand command,
            Principal principal
    ) {
        return sourceReferenceService.setDefault(
                documentId,
                command,
                principal.getName()
        );
    }
}
