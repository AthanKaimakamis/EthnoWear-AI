package fmi.ethnowear.application.model.document.chunk;

import java.util.List;

public record KnowledgeChunkDraft(
        int ordinal,
        String content,
        String contentHash,
        List<ChunkPageContribution> pageContributions
) {

    public KnowledgeChunkDraft {
        pageContributions = List.copyOf(pageContributions);
    }
}
