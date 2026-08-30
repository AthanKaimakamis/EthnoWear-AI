package fmi.ethnowear.application.dto.worker.rendition;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "pageId": 30001,
          "pageMediaId": 40001,
          "mediaAssetId": 50001,
          "renditionType": "PDF_PAGE_RENDER",
          "existing": false
        }
        """)
public record WorkerPageRenditionDetails(
        long pageId,
        long pageMediaId,
        long mediaAssetId,
        WorkerPageRenditionType renditionType,
        boolean existing
) {
}