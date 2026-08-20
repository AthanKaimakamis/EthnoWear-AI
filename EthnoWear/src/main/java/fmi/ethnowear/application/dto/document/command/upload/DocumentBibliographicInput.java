package fmi.ethnowear.application.dto.document.command.upload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DocumentBibliographicInput(
        @Positive Long sourceId,
        @NotBlank @Size(max = 300) String title,
        @Size(max = 200) String author,
        @Size(max = 200) String publisher,
        @Positive Integer publicationYear,
        @Size(max = 10) String language,
        String notes
) {
}
