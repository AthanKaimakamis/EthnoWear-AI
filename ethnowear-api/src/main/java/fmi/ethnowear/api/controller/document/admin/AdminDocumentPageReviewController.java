package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.command.review.CorrectedTextSaveCommand;
import fmi.ethnowear.application.dto.document.command.review.CorrectedTextResetCommand;
import fmi.ethnowear.application.dto.document.command.review.PageApprovalCommand;
import fmi.ethnowear.application.dto.document.command.review.PageRejectionCommand;
import fmi.ethnowear.application.dto.document.command.review.TextSuggestionApplyCommand;
import fmi.ethnowear.application.dto.document.command.review.TextSuggestionIssueApplyCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageReviewDetails;
import fmi.ethnowear.application.dto.document.query.suggestion.DocumentPageTextSuggestionDetails;
import fmi.ethnowear.application.service.document.query.DocumentPageTextSuggestionQueryService;
import fmi.ethnowear.application.service.document.review.DocumentPageReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/document-pages/{pageId}")
@RequiredArgsConstructor
public class AdminDocumentPageReviewController {

    private final DocumentPageReviewService reviewService;
    private final DocumentPageTextSuggestionQueryService suggestionQueryService;

    @GetMapping("/text-suggestions/current")
    public ResponseEntity<DocumentPageTextSuggestionDetails> currentTextSuggestion(
            @PathVariable Long pageId
    ) {
        return suggestionQueryService.findCurrent(pageId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/text-suggestions")
    public Page<DocumentPageTextSuggestionDetails> textSuggestionHistory(
            @PathVariable Long pageId,
            Pageable pageable
    ) {
        return suggestionQueryService.findHistory(pageId, pageable);
    }

    @PostMapping("/text-suggestions/{suggestionId}/apply")
    public DocumentPageReviewDetails applyTextSuggestion(
            @PathVariable Long pageId,
            @PathVariable Long suggestionId,
            @Valid @RequestBody TextSuggestionApplyCommand command,
            Principal principal
    ) {
        return reviewService.applyTextSuggestion(
                pageId,
                suggestionId,
                command,
                principal.getName()
        );
    }

    @PostMapping("/text-suggestions/{suggestionId}/issues/{issueIndex}/apply")
    public DocumentPageReviewDetails applyTextSuggestionIssue(
            @PathVariable Long pageId,
            @PathVariable Long suggestionId,
            @PathVariable int issueIndex,
            @Valid @RequestBody TextSuggestionIssueApplyCommand command,
            Principal principal
    ) {
        return reviewService.applyTextSuggestionIssue(
                pageId,
                suggestionId,
                issueIndex,
                command,
                principal.getName()
        );
    }

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

    @PostMapping("/transcription/reset-from-current-ocr")
    public DocumentPageReviewDetails resetFromCurrentOcr(
            @PathVariable Long pageId,
            @Valid @RequestBody CorrectedTextResetCommand command,
            Principal principal
    ) {
        return reviewService.resetFromCurrentOcr(
                pageId,
                command,
                principal.getName()
        );
    }

    @PostMapping("/approve")
    public DocumentPageReviewDetails approve(
            @PathVariable Long pageId,
            @Valid @RequestBody(required = false) PageApprovalCommand command,
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
