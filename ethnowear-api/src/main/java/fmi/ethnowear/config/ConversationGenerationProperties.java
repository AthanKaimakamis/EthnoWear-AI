package fmi.ethnowear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ethnowear.conversation.generation")
public record ConversationGenerationProperties(
        boolean enabled,
        String ollamaBaseUrl,
        String model,
        Duration timeout,
        int maximumEvidenceCharacters,
        int maximumAnswerCharacters,
        int maximumCitations,
        int maximumHistoryTurns,
        int maximumHistoryCharacters
) {

    public ConversationGenerationProperties {
        if (ollamaBaseUrl == null || ollamaBaseUrl.isBlank())
            throw new IllegalStateException("Conversation Ollama URL is required");

        if (model == null || model.isBlank())
            throw new IllegalStateException("Conversation model is required");

        if (timeout == null || timeout.isZero() || timeout.isNegative())
            throw new IllegalStateException("Conversation timeout must be positive");

        if (maximumEvidenceCharacters <= 0
                || maximumAnswerCharacters <= 0
                || maximumCitations <= 0
                || maximumHistoryTurns <= 0
                || maximumHistoryCharacters <= 0)
            throw new IllegalStateException("Conversation generation limits must be positive");
    }
}