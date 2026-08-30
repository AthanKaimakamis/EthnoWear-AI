package fmi.ethnowear.application.model.document.chunk;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;

public record EligibleChunkPage(
        DocumentPage page,
        String correctedText,
        String correctedTextHash
) {
}
