package fmi.ethnowear.application.dto.retrieval;

public record GroundedPageCitationDetails(
        Long pageId,
        Integer pageSequence,
        Integer pdfPageIndex,
        String printedPageNumber,
        String citationLabel,
        GroundedSourceCitationDetails source
) {
}
