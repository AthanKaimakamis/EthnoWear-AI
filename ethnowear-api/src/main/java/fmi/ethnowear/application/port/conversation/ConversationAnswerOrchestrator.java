package fmi.ethnowear.application.port.conversation;

import fmi.ethnowear.application.dto.conversation.ConversationAnswerDetails;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;

import java.util.function.Consumer;

public interface ConversationAnswerOrchestrator {

    ConversationAnswerDetails generate(
            ConversationTurnExecutionContext context,
            Consumer<ConversationProgressStage> progress
    );
}