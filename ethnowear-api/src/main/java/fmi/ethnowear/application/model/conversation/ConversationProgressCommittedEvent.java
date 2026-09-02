package fmi.ethnowear.application.model.conversation;

import fmi.ethnowear.application.dto.conversation.ConversationProgressDetails;

public record ConversationProgressCommittedEvent(
        ConversationProgressDetails details
) {
}