package fmi.ethnowear.application.service.document.chunk;

import fmi.ethnowear.application.model.document.chunk.ChunkGenerationInput;
import fmi.ethnowear.application.model.document.chunk.EligibleChunkPage;
import fmi.ethnowear.config.DocumentChunkingProperties;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.testutil.EntityTestUtils;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicPageAwareChunkerTest {

    @Test
    void preservesBulgarianTextPageOrderAndOffsets() {
        DocumentChunkingProperties properties = properties(40, 5);
        var chunker = new DeterministicPageAwareChunker(properties);
        Document document = entity(new Document(), 1L);
        DocumentPage first = page(11L, 1, "Българска шевица и орнамент.");
        DocumentPage second = page(12L, 2, "Втори текст за техника.");

        var chunks = chunker.chunk(input(document, first, second));

        assertEquals(2, chunks.size());
        assertEquals(first.getCorrectedText(), chunks.get(0).content());
        assertEquals(second.getCorrectedText(), chunks.get(1).content());
        assertEquals(0, chunks.get(0).pageContributions().getFirst().startCharOffset());
        assertEquals(
                first.getCorrectedText().length(),
                chunks.get(0).pageContributions().getFirst().endCharOffset()
        );
        assertEquals(11L, chunks.get(0).pageContributions().getFirst().page().getId());
        assertEquals(12L, chunks.get(1).pageContributions().getFirst().page().getId());
    }

    @Test
    void prefersParagraphBoundariesAndBoundsOverlap() {
        DocumentChunkingProperties properties = properties(30, 6);
        var chunker = new DeterministicPageAwareChunker(properties);
        String text = "Първи абзац текст.\n\nВтори абзац с достатъчно съдържание.";

        var chunks = chunker.chunk(input(
                entity(new Document(), 1L),
                page(11L, 1, text)
        ));

        assertTrue(chunks.getFirst().content().endsWith("\n\n"));
        for(int i = 1; i < chunks.size(); i++) {
            int previousEnd = chunks.get(i - 1)
                    .pageContributions().getFirst().endCharOffset();
            int currentStart = chunks.get(i)
                    .pageContributions().getFirst().startCharOffset();
            assertTrue(previousEnd - currentStart <= 6);
            assertTrue(currentStart > chunks.get(i - 1)
                    .pageContributions().getFirst().startCharOffset());
        }
    }

    @Test
    void splitsOversizedParagraphAtExactMaximum() {
        DocumentChunkingProperties properties = properties(20, 0);
        var chunker = new DeterministicPageAwareChunker(properties);
        String text = "а".repeat(45);

        var chunks = chunker.chunk(input(
                entity(new Document(), 1L),
                page(11L, 1, text)
        ));

        assertEquals(List.of(20, 20, 5), chunks.stream()
                .map(chunk -> chunk.content().length())
                .toList());
        assertEquals(text, chunks.stream()
                .map(chunk -> chunk.content())
                .reduce("", String::concat));
    }

    private ChunkGenerationInput input(
            Document document,
            DocumentPage... pages
    ) {
        return new ChunkGenerationInput(
                document,
                "0".repeat(64),
                java.util.Arrays.stream(pages)
                        .map(page -> new EligibleChunkPage(
                                page,
                                page.getCorrectedText(),
                                page.getCorrectedTextHash()
                        ))
                        .toList(),
                List.of()
        );
    }

    private DocumentPage page(Long id, int sequence, String text) {
        DocumentPage page = entity(new DocumentPage(), id);
        page.setPageSequence(sequence);
        page.setCorrectedText(text);
        page.setCorrectedTextHash(ContentHashUtils.sha256(text));
        return page;
    }

    private DocumentChunkingProperties properties(int maximum, int overlap) {
        DocumentChunkingProperties properties = new DocumentChunkingProperties();
        properties.setMaximumChunkCharacters(maximum);
        properties.setOverlapCharacters(overlap);
        return properties;
    }

    private <T extends AppendOnlyEntity> T entity(T entity, Long id) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }
}
