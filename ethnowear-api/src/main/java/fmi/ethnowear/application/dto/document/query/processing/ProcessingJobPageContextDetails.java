package fmi.ethnowear.application.dto.document.query.processing;

public record ProcessingJobPageContextDetails(
        Long id,
        Integer pageSequence,
        Integer pdfPageIndex,
        String printedPageNumber,
        String pageLabel
) {
}
