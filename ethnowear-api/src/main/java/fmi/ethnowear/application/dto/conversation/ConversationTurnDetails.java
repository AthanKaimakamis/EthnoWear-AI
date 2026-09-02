package fmi.ethnowear.application.dto.conversation;

import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;

import java.time.Instant;
import java.util.UUID;

public record ConversationTurnDetails(
        UUID conversationId,
        UUID turnId,
        long turnSequence,
        String userMessage,
        ConversationTurnStatus status,
        ConversationProgressStage stage,
        ConversationAnswerDetails answer,
        String errorCode,
        long lastEventId,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {
}