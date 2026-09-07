package fmi.ethnowear.application.port.conversation;

import fmi.ethnowear.application.model.conversation.ConversationHistoryMessage;
import java.util.List;

/** Advisory routing only; never supplies answer text or grants tool access. */
public interface ConversationIntentGateway {
    enum Intent { KNOWLEDGE, HELP, CLARIFY, OUT_OF_SCOPE }
    Intent classify(String question, List<ConversationHistoryMessage> history);
}
