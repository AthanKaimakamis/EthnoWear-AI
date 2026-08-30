package fmi.ethnowear.application.dto.archive.source;

import fmi.ethnowear.domain.model.archive.SourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SourceWriteDto(
        @NotBlank
        String title,
        String author,
        String publisher,
        Integer year,
        @NotNull
        SourceType sourceType,
        String language,
        String filePath,
        String url,
        String isbn,
        String notes,
        boolean trusted
) {
}
