package fmi.ethnowear.application.model.conversation;

import java.util.UUID;

public record ConversationTurnQueuedEvent(UUID conversationId, UUID turnId) {

    public ConversationTurnQueuedEvent {
        if (conversationId == null)
            throw new IllegalArgumentException("Conversation id is required");

        if (turnId == null)
            throw new IllegalArgumentException("Conversation turn id is required");
    }
}