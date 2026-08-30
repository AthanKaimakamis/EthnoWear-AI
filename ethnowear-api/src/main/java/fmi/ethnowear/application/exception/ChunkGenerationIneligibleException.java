package fmi.ethnowear.application.exception;

import fmi.ethnowear.application.dto.document.query.chunk.ChunkGenerationBlockerDetails;

import java.util.List;

public class ChunkGenerationIneligibleException
        extends InvalidDocumentProcessingRequestException {

    private final List<ChunkGenerationBlockerDetails> blockers;

    public ChunkGenerationIneligibleException(
            List<ChunkGenerationBlockerDetails> blockers
    ) {
        super("Document has no eligible approved pages for chunk generation");
        this.blockers = List.copyOf(blockers);
    }

    public List<ChunkGenerationBlockerDetails> getBlockers() {
        return blockers;
    }
}
