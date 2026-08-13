package fmi.ethnowear.api.dto.archive.source;

import java.time.LocalDate;

public record SourceReferenceDataDto(
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