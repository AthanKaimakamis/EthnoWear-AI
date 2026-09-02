package fmi.ethnowear.application.port.conversation;

import fmi.ethnowear.application.model.conversation.ConversationTurnQueuedEvent;

public interface ConversationTurnProcessor {

    void process(ConversationTurnQueuedEvent event);
}