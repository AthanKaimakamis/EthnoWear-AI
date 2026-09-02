package fmi.ethnowear.application.model.conversation;

import java.util.UUID;

public record ConversationProgressStreamTarget(
        long internalTurnId,
        UUID turnId,
        boolean terminal,
        long lastEventId
) {
}