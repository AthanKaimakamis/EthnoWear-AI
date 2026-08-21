package fmi.ethnowear.application.dto.worker.extraction;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(example = """
        {
          "documentId": 20002,
          "pageCount": 2,
          "pages": [
            {"pageId": 30001, "pdfPageIndex": 0, "pageSequence": 1, "renditionRequired": true},
            {"pageId": 30002, "pdfPageIndex": 1, "pageSequence": 2, "renditionRequired": true}
          ]
        }
        """)
public record PageExtractionManifestDetails(
        long documentId,
        int pageCount,
        List<PageExtractionManifestPageDetails> pages
) {

    public PageExtractionManifestDetails {
        pages = List.copyOf(pages);
    }
}