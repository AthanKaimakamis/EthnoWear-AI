package fmi.ethnowear.application.dto.retrieval;

import java.util.List;

public record GroundedRetrievalDetails(
        String question,
        int resultCount,
        List<GroundedPassageDetails> passages,
        boolean insufficientEvidence
) {

    public GroundedRetrievalDetails {
        passages = List.copyOf(passages);
    }

    public GroundedRetrievalDetails(
            String question,
            int resultCount,
            List<GroundedPassageDetails> passages
    ) {
        this(question, resultCount, passages, passages.isEmpty());
    }
}
