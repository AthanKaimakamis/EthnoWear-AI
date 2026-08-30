package fmi.ethnowear.application.service.document.chunk;

import fmi.ethnowear.application.model.document.chunk.ChunkGenerationInput;
import fmi.ethnowear.application.model.document.chunk.ChunkPageContribution;
import fmi.ethnowear.application.model.document.chunk.EligibleChunkPage;
import fmi.ethnowear.application.model.document.chunk.KnowledgeChunkDraft;
import fmi.ethnowear.config.DocumentChunkingProperties;
import fmi.ethnowear.util.ContentHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DeterministicPageAwareChunker {

    private final DocumentChunkingProperties properties;

    public List<KnowledgeChunkDraft> chunk(ChunkGenerationInput input) {
        List<KnowledgeChunkDraft> chunks = new ArrayList<>();
        int ordinal = 0;

        for(EligibleChunkPage page : input.pages()) {
            List<PageSlice> slices = slices(page.correctedText());

            for(PageSlice slice : slices) {
                String content = page.correctedText().substring(
                        slice.start(),
                        slice.end()
                );

                chunks.add(new KnowledgeChunkDraft(
                        ordinal++,
                        content,
                        ContentHashUtils.sha256(content),
                        List.of(new ChunkPageContribution(
                                page.page(),
                                1,
                                slice.start(),
                                slice.end()
                        ))
                ));
            }
        }

        return List.copyOf(chunks);
    }

    private List<PageSlice> slices(String text) {
        List<PageSlice> slices = new ArrayList<>();
        int start = 0;

        while(start < text.length()) {
            int end = preferredEnd(text, start);
            slices.add(new PageSlice(start, end));

            if(end == text.length())
                break;

            start = Math.max(
                    start + 1,
                    end - properties.getOverlapCharacters()
            );
        }

        return slices;
    }

    private int preferredEnd(String text, int start) {
        int maximumEnd = Math.min(
                start + properties.getMaximumChunkCharacters(),
                text.length()
        );

        if(maximumEnd == text.length())
            return maximumEnd;

        int paragraphBoundary = text.lastIndexOf("\n\n", maximumEnd - 1);
        int boundaryEnd = paragraphBoundary + 2;
        int minimumPreferredSize = properties.getMaximumChunkCharacters() / 2;

        if(paragraphBoundary >= start
                && boundaryEnd - start >= minimumPreferredSize)
            return boundaryEnd;

        return maximumEnd;
    }

    private record PageSlice(int start, int end) {
    }
}
