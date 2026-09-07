package fmi.ethnowear.application.service.conversation.turn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.conversation.ConversationAnswerDetails;
import fmi.ethnowear.application.model.conversation.ConversationHistoryMessage;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.config.ConversationGenerationProperties;
import fmi.ethnowear.persistence.jpa.repository.conversation.ConversationTurnRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationHistoryService {

    private final ConversationTurnRepository turns;
    private final ConversationGenerationProperties properties;
    private final ObjectMapper objectMapper;

    public List<ConversationHistoryMessage> recent(@NonNull ConversationTurnExecutionContext context) {
        var recentTurns = turns.findRecentCompleted(
                context.conversationId(),
                context.turnId(),
                PageRequest.of(
                        0,
                        properties.maximumHistoryTurns()
                )
        );

        List<ConversationHistoryMessage> history = new ArrayList<>();
        int remaining = properties.maximumHistoryCharacters();

        for (var turn : recentTurns) {
            ConversationAnswerDetails answer = parseAnswer(turn.getAnswerJson());

            var message = new ConversationHistoryMessage(
                    turn.getUserMessage(),
                    answer.answer(),
                    answer.sources().stream().map(source -> source.citationId()).limit(20).toList(),
                    answer.entityCards().stream().map(card -> card.localName()).limit(12).toList()
            );

            if (message.characterCount() > remaining)
                break;

            history.add(message);
            remaining -= message.characterCount();
        }

        Collections.reverse(history);
        return List.copyOf(history);
    }

    private ConversationAnswerDetails parseAnswer(String json) {
        if (json == null || json.isBlank())
            throw new IllegalStateException("Completed conversation turn has no answer");

        try {
            return objectMapper.readValue(json, ConversationAnswerDetails.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored conversation answer is invalid", ex);
        }
    }
}
