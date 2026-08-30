package fmi.ethnowear.application.model.document.chunk;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;

public record ChunkPageContribution(
        DocumentPage page,
        int pageOrder,
        int startCharOffset,
        int endCharOffset
) {
}
