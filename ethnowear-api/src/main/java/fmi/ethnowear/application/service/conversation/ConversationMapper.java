package fmi.ethnowear.application.service.conversation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.conversation.*;
import fmi.ethnowear.persistence.jpa.entity.conversation.Conversation;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurn;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurnEvent;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class ConversationMapper {

    private final ObjectMapper objectMapper;

    public ConversationDetails toDetails(@NonNull Conversation conversation) {
        return new ConversationDetails(
                conversation.getPublicId(),
                conversation.getTitle(),
                conversation.getLanguage(),
                conversation.getCreatedAt().toInstant(ZoneOffset.UTC),
                conversation.getUpdatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    public ConversationTurnAcceptedDetails toAccepted(@NonNull ConversationTurn turn) {
        return new ConversationTurnAcceptedDetails(
                turn.getConversation().getPublicId(),
                turn.getPublicId(),
                turn.getStatus()
        );
    }

    public ConversationProgressDetails toProgress(@NonNull ConversationTurnEvent event) {
        ConversationTurn turn = event.getTurn();

        return new ConversationProgressDetails(
                event.getEventId(),
                turn.getConversation().getPublicId(),
                turn.getPublicId(),
                event.getStatus(),
                event.getStage(),
                event.getErrorCode(),
                event.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    public ConversationTurnDetails toTurnDetails(@NonNull ConversationTurn turn) {
        return new ConversationTurnDetails(
                turn.getConversation().getPublicId(),
                turn.getPublicId(),
                turn.getTurnSequence(),
                turn.getUserMessage(),
                turn.getStatus(),
                turn.getStage(),
                parseAnswer(turn.getAnswerJson()),
                turn.getErrorCode(),
                turn.getLastEventId(),
                instant(turn.getCreatedAt()),
                instant(turn.getStartedAt()),
                instant(turn.getFinishedAt())
        );
    }

    private ConversationAnswerDetails parseAnswer(String json) {
        if (json == null)
            return null;

        try {
            return objectMapper.readValue(json, ConversationAnswerDetails.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Stored conversation answer is invalid",
                    ex
            );
        }
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}