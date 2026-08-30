package fmi.ethnowear.application.dto.document.query.suggestion;

import fmi.ethnowear.application.dto.IdentifiableDto;

import java.time.LocalDateTime;
import java.util.List;

public record DocumentPageTextSuggestionDetails(
        Long id,
        Long documentPageId,
        Long documentPageMediaId,
        Long documentPageOcrResultId,
        Long processingJobId,
        String suggestedText,
        String modelName,
        String modelVersion,
        String promptVersion,
        boolean requiresReview,
        String editableTextHash,
        List<TextSuggestionIssueDetails> issues,
        List<TextSuggestionUncertainPassageDetails> uncertainPassages,
        boolean applied,
        boolean requiresHumanAttention,
        LocalDateTime createdAt
) implements IdentifiableDto {

    public DocumentPageTextSuggestionDetails {
        issues = List.copyOf(issues);
        uncertainPassages = List.copyOf(uncertainPassages);
    }
}
