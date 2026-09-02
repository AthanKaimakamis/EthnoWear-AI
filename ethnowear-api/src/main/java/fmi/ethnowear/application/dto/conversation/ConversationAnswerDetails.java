package fmi.ethnowear.application.dto.conversation;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

public record ConversationAnswerDetails(
        UUID conversationId,
        UUID turnId,
        String answer,
        boolean insufficientEvidence,
        List<ConversationSourceDetails> sources,
        List<ConversationEntityCardDetails> entityCards,
        List<ConversationArchiveCardDetails> archiveCards,
        List<ConversationMediaDetails> media,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<ConversationActionDetails> actions,
        List<String> warningCodes
) {

    public ConversationAnswerDetails {
        sources = List.copyOf(sources);
        entityCards = List.copyOf(entityCards);
        archiveCards = List.copyOf(archiveCards);
        media = List.copyOf(media);
        actions = actions == null ? List.of() : List.copyOf(actions);
        warningCodes = List.copyOf(warningCodes);
    }

    public ConversationAnswerDetails(
            UUID conversationId,
            UUID turnId,
            String answer,
            boolean insufficientEvidence,
            List<ConversationSourceDetails> sources,
            List<ConversationEntityCardDetails> entityCards,
            List<ConversationArchiveCardDetails> archiveCards,
            List<ConversationMediaDetails> media,
            List<String> warningCodes
    ) {
        this(
                conversationId,
                turnId,
                answer,
                insufficientEvidence,
                sources,
                entityCards,
                archiveCards,
                media,
                List.of(),
                warningCodes
        );
    }

    public ConversationAnswerDetails(
            UUID conversationId,
            UUID turnId,
            String answer,
            boolean insufficientEvidence,
            List<ConversationSourceDetails> sources,
            List<ConversationEntityCardDetails> entityCards,
            List<ConversationArchiveCardDetails> archiveCards,
            List<String> warningCodes
    ) {
        this(
                conversationId,
                turnId,
                answer,
                insufficientEvidence,
                sources,
                entityCards,
                archiveCards,
                List.of(),
                List.of(),
                warningCodes
        );
    }
}
