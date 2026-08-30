package fmi.ethnowear.application.dto.retrieval;

public record GroundedSourceCitationDetails(
        Long sourceId,
        String sourceTitle,
        String author,
        String publisher,
        Integer publicationYear,
        Long sourceReferenceId,
        String chapter,
        Integer pageFrom,
        Integer pageTo,
        String figureNumber,
        String sectionTitle,
        String locator
) {
}