package fmi.ethnowear.application.dto.document.command.review;

import jakarta.validation.constraints.AssertTrue;

public record CorrectedTextResetCommand(
        @AssertTrue(message = "Corrected-text reset must be explicitly confirmed")
        boolean confirmed
) {
}
