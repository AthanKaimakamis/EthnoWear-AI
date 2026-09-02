package fmi.ethnowear.application.port.conversation;

import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;

import java.util.function.Consumer;

public interface ConversationEvidenceCollector {

    ConversationEvidenceBundle collect(
            ConversationTurnExecutionContext context,
            Consumer<ConversationProgressStage> progress
    );
}