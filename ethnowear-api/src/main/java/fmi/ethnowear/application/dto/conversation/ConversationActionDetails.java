package fmi.ethnowear.application.dto.conversation;

import fmi.ethnowear.domain.model.conversation.ConversationActionType;
import fmi.ethnowear.domain.model.conversation.ConversationArchiveTarget;

public record ConversationActionDetails(
        ConversationActionType type,
        String label,
        ConversationArchiveTarget target,
        ConversationArchiveFiltersDetails filters
) {
}
