package fmi.ethnowear.application.dto.conversation;

import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;

import java.util.UUID;

public record ConversationTurnAcceptedDetails(
        UUID conversationId,
        UUID turnId,
        ConversationTurnStatus status
) {
}