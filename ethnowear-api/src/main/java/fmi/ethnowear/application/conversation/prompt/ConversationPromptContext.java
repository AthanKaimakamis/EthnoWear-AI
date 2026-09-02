package fmi.ethnowear.application.conversation.prompt;

import fmi.ethnowear.application.model.conversation.ConversationGenerationRequest;
import fmi.ethnowear.application.model.conversation.ConversationHistoryMessage;
import fmi.ethnowear.application.model.conversation.ConversationReasoningResult;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record ConversationPromptContext(
        List<ConversationHistoryMessage> recentConversationHistory,
        ConversationPromptEvidence authoritativeEvidence,
        ConversationReasoningResult advisoryAgentReasoning
) {

    public ConversationPromptContext {
        recentConversationHistory = List.copyOf(recentConversationHistory);
    }

    @Contract("_ -> new")
    public static @NonNull ConversationPromptContext from(@NonNull ConversationGenerationRequest request) {
        return new ConversationPromptContext(
                request.history(),
                ConversationPromptEvidence.from(request.evidence()),
                request.reasoning()
        );
    }
}