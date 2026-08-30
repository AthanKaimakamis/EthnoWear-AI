package fmi.ethnowear.application.dto.document.command.metadata;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DocumentPageMetadataUpdateCommand(
        @Size(max = 50) String printedPageNumber,
        @PositiveOrZero Integer printedPageSort,
        @Size(max = 100) String pageLabel
) {
}
