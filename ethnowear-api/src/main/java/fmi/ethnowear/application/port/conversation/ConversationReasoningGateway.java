package fmi.ethnowear.application.port.conversation;

import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationReasoningResult;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;

public interface ConversationReasoningGateway {

    ConversationReasoningResult reason(
            ConversationTurnExecutionContext context,
            ConversationEvidenceBundle evidence
    );
}