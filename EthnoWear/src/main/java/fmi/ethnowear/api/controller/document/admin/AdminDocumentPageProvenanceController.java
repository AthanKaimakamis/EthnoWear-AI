package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.command.provenance.CanonicalPageLinkCommand;
import fmi.ethnowear.application.dto.document.command.provenance.CanonicalPageLinkReversalCommand;
import fmi.ethnowear.application.dto.document.command.provenance.PageProvenanceTrustChangeCommand;
import fmi.ethnowear.application.dto.document.command.provenance.PageSourceProvenanceChangeCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageProvenanceEventDetails;
import fmi.ethnowear.application.service.document.provenance.DocumentPageProvenanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/document-pages/{pageId}/provenance-events")
@RequiredArgsConstructor
public class AdminDocumentPageProvenanceController {

    private final DocumentPageProvenanceService provenanceService;

    @PostMapping("/source-change")
    public DocumentPageProvenanceEventDetails changeSource(
            @PathVariable Long pageId,
            @Valid @RequestBody PageSourceProvenanceChangeCommand command,
            Principal principal
    ) {
        return provenanceService.changeSource(
                pageId,
                command,
                principal.getName()
        );
    }

    @PostMapping("/trust-change")
    public DocumentPageProvenanceEventDetails changeTrust(
            @PathVariable Long pageId,
            @Valid @RequestBody PageProvenanceTrustChangeCommand command,
            Principal principal
    ) {
        return provenanceService.changeTrust(
                pageId,
                command,
                principal.getName()
        );
    }

    @PostMapping("/canonical-link")
    public DocumentPageProvenanceEventDetails linkCanonicalPage(
            @PathVariable Long pageId,
            @Valid @RequestBody CanonicalPageLinkCommand command,
            Principal principal
    ) {
        return provenanceService.linkCanonicalPage(
                pageId,
                command,
                principal.getName()
        );
    }

    @PostMapping("/canonical-merge")
    public DocumentPageProvenanceEventDetails mergeIntoCanonicalPage(
            @PathVariable Long pageId,
            @Valid @RequestBody CanonicalPageLinkCommand command,
            Principal principal
    ) {
        return provenanceService.mergeIntoCanonicalPage(
                pageId,
                command,
                principal.getName()
        );
    }

    @PostMapping("/canonical-link-reversal")
    public DocumentPageProvenanceEventDetails reverseCanonicalLink(
            @PathVariable Long pageId,
            @Valid @RequestBody CanonicalPageLinkReversalCommand command,
            Principal principal
    ) {
        return provenanceService.reverseCanonicalLink(
                pageId,
                command.reason(),
                principal.getName()
        );
    }
}
