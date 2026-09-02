package fmi.ethnowear.application.model.conversation;

import java.util.List;
import java.util.Objects;

public record ConversationGenerationRequest(
        String question,
        String language,
        List<ConversationHistoryMessage> history,
        ConversationEvidenceBundle evidence,
        ConversationReasoningResult reasoning
) {

    public ConversationGenerationRequest {
        if (question == null || question.isBlank())
            throw new IllegalArgumentException("Conversation question is required");

        if (language == null || language.isBlank())
            throw new IllegalArgumentException("Conversation language is required");

        question = question.trim();
        language = language.trim();
        history = List.copyOf(history);
        evidence = Objects.requireNonNull(evidence);
        reasoning = Objects.requireNonNull(reasoning);
    }
}