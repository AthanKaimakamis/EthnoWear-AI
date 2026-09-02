package fmi.ethnowear.application.dto.archive.source;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.archive.SourceType;
import fmi.ethnowear.domain.model.rights.RightsStatus;

import java.time.LocalDateTime;

public record SourceDetails(
        Long id,
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
        boolean trusted,
        RightsStatus rightsStatus,
        String license,
        boolean publicDisplayAllowed,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {

    public SourceDetails(
            Long id,
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
            boolean trusted,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                id, title, author, publisher, year, sourceType, language,
                filePath, url, isbn, notes, trusted,
                RightsStatus.UNKNOWN, null, false,
                createdAt, updatedAt
        );
    }
}
