package fmi.ethnowear.application.model.document.chunk;

import fmi.ethnowear.application.dto.document.query.chunk.ChunkGenerationBlockerDetails;
import fmi.ethnowear.persistence.jpa.entity.document.Document;

import java.util.List;

public record ChunkGenerationInput(
        Document document,
        String generationInputHash,
        List<EligibleChunkPage> pages,
        List<ChunkGenerationBlockerDetails> blockers
) {

    public ChunkGenerationInput {
        pages = List.copyOf(pages);
        blockers = List.copyOf(blockers);
    }
}
