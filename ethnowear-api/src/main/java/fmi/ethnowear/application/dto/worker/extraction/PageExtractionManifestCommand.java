package fmi.ethnowear.application.dto.worker.extraction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(example = """
        {
          "totalPageCount": 2,
          "pages": [
            {"pdfPageIndex": 0, "pageSequence": 1},
            {"pdfPageIndex": 1, "pageSequence": 2}
          ]
        }
        """)
public record PageExtractionManifestCommand(
        @Positive
        int totalPageCount,

        @NotEmpty
        @Size(max = 5000)
        List<@Valid PageExtractionManifestPageCommand> pages
) {

    public PageExtractionManifestCommand {
        if(pages != null)
            pages = List.copyOf(pages);
    }
}