package fmi.ethnowear.application.dto.document.command.review;

import jakarta.validation.constraints.AssertTrue;

public record TextSuggestionApplyCommand(
        @AssertTrue(message = "Suggestion application must be explicitly confirmed")
        boolean confirmed
) {
}
