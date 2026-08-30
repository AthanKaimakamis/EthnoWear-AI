package fmi.ethnowear.application.dto.document.query.chunk;

public record GeneratedChunkCitationDetails(
        Long documentPageId,
        Integer pageOrder,
        Integer startCharOffset,
        Integer endCharOffset,
        boolean startsOnPage,
        boolean endsOnPage,
        String printedPageNumber,
        Integer pdfPageIndex,
        String label
) {
}
