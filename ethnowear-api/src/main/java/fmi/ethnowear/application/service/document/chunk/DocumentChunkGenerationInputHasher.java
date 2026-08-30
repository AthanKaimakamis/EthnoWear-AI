package fmi.ethnowear.application.service.document.chunk;

import fmi.ethnowear.application.model.document.chunk.EligibleChunkPage;
import fmi.ethnowear.config.DocumentChunkingProperties;
import fmi.ethnowear.util.ContentHashUtils;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentChunkGenerationInputHasher {

    public String hash(
            List<EligibleChunkPage> pages,
            DocumentChunkingProperties properties
    ) {
        StringBuilder input = new StringBuilder()
                .append(properties.getStrategy()).append('|')
                .append(properties.getVersion()).append('|')
                .append(properties.getMaximumChunkCharacters()).append('|')
                .append(properties.getOverlapCharacters()).append('\n');

        pages.forEach(page -> input
                .append(page.page().getId()).append('|')
                .append(page.correctedTextHash()).append('|')
                .append(page.page().getProvenanceStatus()).append('|')
                .append(page.page().getProvenanceTrustState()).append('|')
                .append(page.page().getSourceReference() == null
                        ? ""
                        : page.page().getSourceReference().getId())
                .append('|')
                .append(page.page().getPdfPageIndex() == null
                        ? ""
                        : page.page().getPdfPageIndex())
                .append('|')
                .append(page.page().getPrintedPageNumber() == null
                        ? ""
                        : page.page().getPrintedPageNumber())
                .append('|')
                .append(page.page().getPrintedPageSort() == null
                        ? ""
                        : page.page().getPrintedPageSort())
                .append('|')
                .append(page.page().getPageLabel() == null
                        ? ""
                        : page.page().getPageLabel())
                .append('\n'));

        return ContentHashUtils.sha256(input.toString());
    }
}
