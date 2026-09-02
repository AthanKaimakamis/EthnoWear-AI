package fmi.ethnowear.application.port.conversation;

import fmi.ethnowear.application.model.conversation.ConversationGenerationRequest;
import fmi.ethnowear.application.model.conversation.ConversationGenerationResult;

public interface ConversationGenerationGateway {

    ConversationGenerationResult generate(ConversationGenerationRequest request);
}