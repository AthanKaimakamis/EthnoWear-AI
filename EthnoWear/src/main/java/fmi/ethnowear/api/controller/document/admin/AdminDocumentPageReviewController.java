package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.command.review.CorrectedTextSaveCommand;
import fmi.ethnowear.application.dto.document.command.review.PageApprovalCommand;
import fmi.ethnowear.application.dto.document.command.review.PageRejectionCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageReviewDetails;
import fmi.ethnowear.application.service.document.review.DocumentPageReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/document-pages/{pageId}")
@RequiredArgsConstructor
public class AdminDocumentPageReviewController {

    private final DocumentPageReviewService reviewService;

    @PatchMapping("/transcription")
    public DocumentPageReviewDetails saveCorrectedText(
            @PathVariable Long pageId,
            @Valid @RequestBody CorrectedTextSaveCommand command,
            Principal principal
    ) {
        return reviewService.saveCorrectedText(
                pageId,
                command,
                principal.getName()
        );
    }

    @PostMapping("/approve")
    public DocumentPageReviewDetails approve(
            @PathVariable Long pageId,
            @Valid @RequestBody PageApprovalCommand command,
            Principal principal
    ) {
        return reviewService.approve(
                pageId,
                command,
                principal.getName()
        );
    }

    @PostMapping("/reject")
    public DocumentPageReviewDetails reject(
            @PathVariable Long pageId,
            @Valid @RequestBody PageRejectionCommand command,
            Principal principal
    ) {
        return reviewService.reject(
                pageId,
                command,
                principal.getName()
        );
    }
}