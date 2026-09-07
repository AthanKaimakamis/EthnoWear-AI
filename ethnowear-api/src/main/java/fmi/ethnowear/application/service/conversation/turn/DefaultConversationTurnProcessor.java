package fmi.ethnowear.application.service.conversation.turn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.conversation.ConversationAnswerDetails;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.application.model.conversation.ConversationTurnQueuedEvent;
import fmi.ethnowear.application.port.conversation.*;
import fmi.ethnowear.application.exception.ConversationTurnCancelledException;
import fmi.ethnowear.application.exception.ConversationGenerationUnavailableException;
import fmi.ethnowear.application.exception.ConversationGenerationRejectedException;
import fmi.ethnowear.application.exception.RetrievalUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(ConversationAnswerOrchestrator.class)
public class DefaultConversationTurnProcessor implements ConversationTurnProcessor {

    private final ConversationTurnLifecycleService lifecycle;
    private final ConversationAnswerOrchestrator orchestrator;
    private final ObjectMapper objectMapper;

    @Override
    public void process(ConversationTurnQueuedEvent identity) {
        var context = lifecycle.start(identity).orElse(null);

        if (context == null)
            return;

        try {
            var answer = orchestrator.generate(
                    context,
                    stage -> {
                        if (!lifecycle.advance(identity, stage))
                            throw new ConversationTurnCancelledException();
                    }
            );

            validateIdentity(context, answer);

            lifecycle.complete(identity, objectMapper.writeValueAsString(answer));
        } catch (ConversationTurnCancelledException ex) {
            log.debug(
                    "Conversation turn {} was cancelled",
                    identity.turnId()
            );
        }
        catch (ConversationGenerationRejectedException ex) {
            fail(identity, ex, "CONVERSATION_GROUNDING_FAILED");
        }
        catch (ConversationGenerationUnavailableException ex) {
            fail(identity, ex, "CONVERSATION_OLLAMA_UNAVAILABLE");
        }
        catch (RetrievalUnavailableException ex) {
            fail(identity, ex, "CONVERSATION_RAG_UNAVAILABLE");
        }
        catch (RuntimeException | JsonProcessingException ex) {
            fail(identity, ex, "CONVERSATION_PROCESSING_FAILED");
        }
    }

    private void fail(ConversationTurnQueuedEvent identity, Exception exception, String code) {
            log.error(
                    "Conversation turn {} failed during processing: {} at {}",
                    identity.turnId(),
                    exception.getClass().getSimpleName(),
                    java.util.Arrays.stream(exception.getStackTrace()).limit(12).toList()
            );
            lifecycle.fail(identity, code);
    }

    private void validateIdentity(ConversationTurnExecutionContext context, ConversationAnswerDetails answer) {
        if (answer == null
                || !context.conversationId().equals(answer.conversationId())
                || !context.turnId().equals(answer.turnId()))
            throw new IllegalArgumentException("Generated answer identity does not match its turn");
    }
}
