package fmi.ethnowear.agents.protocol;

import java.util.List;

public record InterpretationResultPayload(
        String conversationId,
        List<CandidatePayload> candidates,
        String explanation,
        List<String> warnings
) {
}
