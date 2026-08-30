package fmi.ethnowear.application.dto.document.command.review;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TextSuggestionIssueApplyCommand(
        @AssertTrue(message = "Suggestion issue application must be explicitly confirmed")
        boolean confirmed,
        @NotBlank
        @Pattern(regexp = "[0-9a-f]{64}")
        String expectedCorrectedTextHash
) {
}
