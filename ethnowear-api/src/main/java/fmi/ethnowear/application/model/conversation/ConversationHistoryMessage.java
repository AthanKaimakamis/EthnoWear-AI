package fmi.ethnowear.application.model.conversation;

public record ConversationHistoryMessage(
        String userMessage,
        String assistantAnswer,
        java.util.List<String> evidenceIds,
        java.util.List<String> entityLocalNames
) {

    public ConversationHistoryMessage {
        evidenceIds = evidenceIds == null ? java.util.List.of() : java.util.List.copyOf(evidenceIds);
        entityLocalNames = entityLocalNames == null ? java.util.List.of() : java.util.List.copyOf(entityLocalNames);
        if (userMessage == null || userMessage.isBlank())
            throw new IllegalArgumentException("Historical user message is required");

        if (assistantAnswer == null || assistantAnswer.isBlank())
            throw new IllegalArgumentException("Historical assistant answer is required");
    }

    public ConversationHistoryMessage(String userMessage, String assistantAnswer) {
        this(userMessage, assistantAnswer, java.util.List.of(), java.util.List.of());
    }

    public int characterCount() {
        return userMessage.length() + assistantAnswer.length()
                + evidenceIds.stream().mapToInt(String::length).sum()
                + entityLocalNames.stream().mapToInt(String::length).sum();
    }
}
