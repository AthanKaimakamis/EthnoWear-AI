package fmi.ethnowear.application.dto.retrieval;

import java.util.List;

public record GroundedRetrievalDetails(
        String question,
        int resultCount,
        List<GroundedPassageDetails> passages
) {

    public GroundedRetrievalDetails {
        passages = List.copyOf(passages);
    }
}