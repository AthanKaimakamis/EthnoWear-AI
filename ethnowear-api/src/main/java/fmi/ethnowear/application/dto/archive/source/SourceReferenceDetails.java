package fmi.ethnowear.application.dto.archive.source;

import fmi.ethnowear.application.dto.IdentifiableDto;

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
) implements IdentifiableDto {
}