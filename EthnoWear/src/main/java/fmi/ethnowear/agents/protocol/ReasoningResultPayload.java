package fmi.ethnowear.agents.protocol;

import java.util.List;

public record ReasoningResultPayload(
        String conversationId,
        List<CandidatePayload> candidates
) {
}
