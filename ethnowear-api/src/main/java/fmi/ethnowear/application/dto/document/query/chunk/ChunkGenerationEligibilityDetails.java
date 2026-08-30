package fmi.ethnowear.application.dto.document.query.chunk;

import java.util.List;

public record ChunkGenerationEligibilityDetails(
        Long documentId,
        boolean eligible,
        int eligiblePageCount,
        List<ChunkGenerationBlockerDetails> blockers
) {

    public ChunkGenerationEligibilityDetails {
        blockers = List.copyOf(blockers);
    }
}
