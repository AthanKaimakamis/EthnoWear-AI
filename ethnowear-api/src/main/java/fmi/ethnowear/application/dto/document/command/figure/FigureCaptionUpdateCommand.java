package fmi.ethnowear.application.dto.document.command.figure;

import jakarta.validation.constraints.Size;

public record FigureCaptionUpdateCommand(
        @Size(max = 2000) String correctedCaptionText,
        @Size(max = 100) String printedFigureNumber,
        Long sourceReferenceId
) {
}
