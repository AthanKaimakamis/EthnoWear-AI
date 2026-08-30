package fmi.ethnowear.application.dto.document.query.suggestion;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.math.BigDecimal;

public record TextSuggestionIssueDetails(
        @JsonAlias("code") String issueType,
        @JsonAlias("safeMessage") String explanationBg,
        BigDecimal confidence,
        String originalText,
        String originalContext,
        String suggestedText,
        String suggestedContext,
        Integer startOffset,
        Integer endOffset,
        boolean safelyApplicable
) {
}
