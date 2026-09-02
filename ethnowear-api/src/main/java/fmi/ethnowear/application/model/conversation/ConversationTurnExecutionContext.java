package fmi.ethnowear.application.model.conversation;

import java.util.UUID;

public record ConversationTurnExecutionContext(
        UUID conversationId,
        UUID turnId,
        String language,
        String userMessage
) {
}