package fmi.ethnowear.application.dto.archive.source;

import fmi.ethnowear.domain.model.archive.SourceType;
import fmi.ethnowear.domain.model.rights.RightsStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
        boolean trusted,
        RightsStatus rightsStatus,
        @Size(max = 500)
        String license,
        boolean publicDisplayAllowed
) {

    public SourceWriteDto(
            String title,
            String author,
            String publisher,
            Integer year,
            SourceType sourceType,
            String language,
            String filePath,
            String url,
            String isbn,
            String notes,
            boolean trusted
    ) {
        this(
                title, author, publisher, year, sourceType, language,
                filePath, url, isbn, notes, trusted,
                RightsStatus.UNKNOWN, null, false
        );
    }
}
