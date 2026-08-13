package fmi.ethnowear.api.dto.archive.source;

import fmi.ethnowear.application.enums.SourceType;

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
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

}