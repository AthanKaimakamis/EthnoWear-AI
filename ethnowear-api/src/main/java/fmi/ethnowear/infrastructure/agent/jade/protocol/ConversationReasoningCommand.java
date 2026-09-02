package fmi.ethnowear.infrastructure.agent.jade.protocol;

import fmi.ethnowear.application.model.conversation.ConversationReasoningResult;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public record ConversationReasoningCommand(
        ConversationReasoningPayload payload,
        CompletableFuture<ConversationReasoningResult> result
) {

    public ConversationReasoningCommand {
        payload = Objects.requireNonNull(payload);
        result = Objects.requireNonNull(result);
    }
}