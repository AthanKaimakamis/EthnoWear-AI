package fmi.ethnowear.infrastructure.agent.jade.protocol;

import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;

import java.util.Objects;
import java.util.UUID;

public record ConversationReasoningPayload(
        UUID requestId,
        ConversationTurnExecutionContext context,
        ConversationEvidenceBundle evidence
) {

    public ConversationReasoningPayload {
        requestId = Objects.requireNonNull(requestId);
        context = Objects.requireNonNull(context);
        evidence = Objects.requireNonNull(evidence);
    }
}