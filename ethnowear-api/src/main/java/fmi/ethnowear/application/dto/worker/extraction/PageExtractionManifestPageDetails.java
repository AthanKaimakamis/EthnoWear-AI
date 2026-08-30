package fmi.ethnowear.application.dto.worker.extraction;

public record PageExtractionManifestPageDetails(
        long pageId,
        int pdfPageIndex,
        int pageSequence,
        boolean renditionRequired
) {
}