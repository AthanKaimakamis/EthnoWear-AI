package fmi.ethnowear.application.dto.document.command.review;

import jakarta.validation.constraints.NotBlank;

public record CorrectedTextSaveCommand(
        @NotBlank String correctedText
){
}
