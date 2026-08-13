package fmi.ethnowear.api.dto.archive.query;

import fmi.ethnowear.application.enums.SourceType;

import java.time.LocalDate;

public record EntitySourceCitationDetails(
        Long sourceReferenceId,
        Long sourceId,
        String title,
        String author,
        String publisher,
        Integer year,
        SourceType sourceType,
        String sourceLanguage,
        String filePath,
        String url,
        String isbn,
        boolean trusted,
        String chapter,
        Integer pageFrom,
        Integer pageTo,
        String figureNumber,
        String sectionTitle,
        String catalogNumber,
        String referenceUrl,
        LocalDate accessedDate,
        String locator,
        String note
) {
}