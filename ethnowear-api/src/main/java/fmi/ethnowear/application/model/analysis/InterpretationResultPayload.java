package fmi.ethnowear.application.model.analysis;

import java.util.List;

public record InterpretationResultPayload(
        String conversationId,
        List<CandidatePayload> candidates,
        String explanation,
        List<String> warnings
) {
}
