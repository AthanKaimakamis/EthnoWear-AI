package fmi.ethnowear.application.dto.conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationDetails(
        UUID conversationId,
        String title,
        String language,
        Instant createdAt,
        Instant updatedAt
) {
}