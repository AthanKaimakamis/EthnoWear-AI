package fmi.ethnowear.application.dto.document.command.review;

import jakarta.validation.constraints.Size;

public record PageApprovalCommand(
        @Size(max = 1000) String notes
) {
}
