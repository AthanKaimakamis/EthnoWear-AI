package fmi.ethnowear.application.dto.document.query;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.archive.SourceType;

public record DocumentSourceDetails(
        Long id,
        String title,
        String author,
        String publisher,
        Integer publicationYear,
        SourceType sourceType,
        String language,
        String url,
        String isbn,
        String notes,
        boolean trusted
) implements IdentifiableDto {
}
