package fmi.ethnowear.application.dto.worker.rendition;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(example = """
        {
          "pdfPageIndex": 0,
          "pageSequence": 1,
          "renditionType": "PDF_PAGE_RENDER",
          "dpi": 300,
          "colorMode": "RGB",
          "pixelWidth": 2480,
          "pixelHeight": 3508,
          "rendererName": "PyMuPDF",
          "rendererVersion": "1.26.0"
        }
        """)
public record WorkerPageRenditionCommand(
        @NotNull
        @PositiveOrZero
        Integer pdfPageIndex,

        @NotNull
        @Positive
        Integer pageSequence,

        @NotNull
        WorkerPageRenditionType renditionType,

        @Positive
        Integer dpi,

        @NotNull
        WorkerColorMode colorMode,

        @Positive
        Integer pixelWidth,

        @Positive
        Integer pixelHeight,

        @NotBlank
        @Size(max = 100)
        String rendererName,

        @Size(max = 100)
        String rendererVersion
) {
}