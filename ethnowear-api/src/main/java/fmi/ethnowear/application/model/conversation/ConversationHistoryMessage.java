package fmi.ethnowear.application.model.conversation;

public record ConversationHistoryMessage(
        String userMessage,
        String assistantAnswer
) {

    public ConversationHistoryMessage {
        if (userMessage == null || userMessage.isBlank())
            throw new IllegalArgumentException("Historical user message is required");

        if (assistantAnswer == null || assistantAnswer.isBlank())
            throw new IllegalArgumentException("Historical assistant answer is required");
    }

    public int characterCount() {
        return userMessage.length() + assistantAnswer.length();
    }
}