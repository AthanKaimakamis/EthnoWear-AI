package fmi.ethnowear.application.model.analysis;

import java.util.List;

public record ReasoningResultPayload(
        String conversationId,
        List<CandidatePayload> candidates
) {
}
