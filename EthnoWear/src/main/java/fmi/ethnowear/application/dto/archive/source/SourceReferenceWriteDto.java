package fmi.ethnowear.application.dto.archive.source;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SourceReferenceWriteDto(
        @NotNull
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
        String note
) {
}
