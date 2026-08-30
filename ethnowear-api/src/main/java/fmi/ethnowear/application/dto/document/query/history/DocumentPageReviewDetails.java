package fmi.ethnowear.application.dto.document.query.history;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.document.review.ReviewAction;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;

import java.time.LocalDateTime;

public record DocumentPageReviewDetails(
        Long id,
        Long documentPageId,
        Long sourceTextSuggestionId,
        Integer sourceTextSuggestionIssueOrdinal,
        ReviewAction reviewAction,
        String reviewer,
        String correctedTextSnapshot,
        ReviewState previousReviewState,
        ReviewState newReviewState,
        TranscriptionApprovalState previousApprovalState,
        TranscriptionApprovalState newApprovalState,
        String reason,
        LocalDateTime createdAt
) implements IdentifiableDto {
}
