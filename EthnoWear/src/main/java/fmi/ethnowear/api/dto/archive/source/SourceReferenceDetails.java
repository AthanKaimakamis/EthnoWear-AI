package fmi.ethnowear.api.dto.archive.source;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SourceReferenceDetails(
        Long id,
        Long sourceId,
        String chapter,
        Integer pageFrom,
        Integer pageTo,
        String figureNumber,
        String sectionTitle,
        String catalogNumber,
        String referenceUrl,
        LocalDate accessedDate,
        String locator,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}