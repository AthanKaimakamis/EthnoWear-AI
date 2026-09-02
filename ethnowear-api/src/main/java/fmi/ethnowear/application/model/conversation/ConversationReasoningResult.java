package fmi.ethnowear.application.model.conversation;

import java.util.List;

public record ConversationReasoningResult(
        List<ConversationAgentFinding> findings,
        List<String> warningCodes
) {

    public ConversationReasoningResult {
        findings = List.copyOf(findings);
        warningCodes = List.copyOf(warningCodes);
    }
}