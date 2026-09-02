package fmi.ethnowear.application.dto.conversation;

import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;

import java.time.Instant;
import java.util.UUID;

public record ConversationProgressDetails(
        long eventId,
        UUID conversationId,
        UUID turnId,
        ConversationTurnStatus status,
        ConversationProgressStage stage,
        String errorCode,
        Instant occurredAt
) {
}