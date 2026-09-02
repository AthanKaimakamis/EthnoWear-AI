package fmi.ethnowear.application.service.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.conversation.ConversationAnswerDetails;
import fmi.ethnowear.application.model.conversation.*;
import fmi.ethnowear.application.port.conversation.ConversationAnswerOrchestrator;
import fmi.ethnowear.application.exception.ConversationGenerationRejectedException;
import fmi.ethnowear.application.exception.ConversationGenerationUnavailableException;
import fmi.ethnowear.application.service.conversation.turn.ConversationTurnLifecycleService;
import fmi.ethnowear.application.service.conversation.turn.DefaultConversationTurnProcessor;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultConversationTurnProcessorTest {
    private final ConversationTurnLifecycleService lifecycle = mock(ConversationTurnLifecycleService.class);
    private final ConversationAnswerOrchestrator orchestrator = mock(ConversationAnswerOrchestrator.class);
    private final DefaultConversationTurnProcessor processor = new DefaultConversationTurnProcessor(
            lifecycle, orchestrator, new ObjectMapper()
    );
    private final UUID conversationId = UUID.randomUUID();
    private final UUID turnId = UUID.randomUUID();
    private final ConversationTurnQueuedEvent identity = new ConversationTurnQueuedEvent(conversationId, turnId);
    private final ConversationTurnExecutionContext context = new ConversationTurnExecutionContext(
            conversationId, turnId, "bg", "Въпрос"
    );

    @Test
    void validAnswerCompletesClaimedTurn() {
        var answer = new ConversationAnswerDetails(
                conversationId, turnId, "Отговор", false,
                List.of(), List.of(), List.of(), List.of()
        );
        when(lifecycle.start(identity)).thenReturn(Optional.of(context));
        when(orchestrator.generate(eq(context), any())).thenReturn(answer);

        processor.process(identity);

        verify(lifecycle).complete(eq(identity), contains("Отговор"));
        verify(lifecycle, never()).fail(any(), anyString());
    }

    @Test
    void mismatchedAnswerFailsWithoutPersistingIt() {
        var answer = new ConversationAnswerDetails(
                UUID.randomUUID(), turnId, "Wrong", false,
                List.of(), List.of(), List.of(), List.of()
        );
        when(lifecycle.start(identity)).thenReturn(Optional.of(context));
        when(orchestrator.generate(eq(context), any())).thenReturn(answer);

        processor.process(identity);

        verify(lifecycle).fail(identity, "CONVERSATION_PROCESSING_FAILED");
        verify(lifecycle, never()).complete(any(), anyString());
    }

    @Test
    void rejectedGroundingIsNotReportedAsOllamaOutage() {
        when(lifecycle.start(identity)).thenReturn(Optional.of(context));
        when(orchestrator.generate(eq(context), any()))
                .thenThrow(new ConversationGenerationRejectedException("Unsupported claim"));

        processor.process(identity);

        verify(lifecycle).fail(identity, "CONVERSATION_GROUNDING_FAILED");
    }

    @Test
    void actualGenerationOutageIsReportedAsOllamaUnavailable() {
        when(lifecycle.start(identity)).thenReturn(Optional.of(context));
        when(orchestrator.generate(eq(context), any()))
                .thenThrow(new ConversationGenerationUnavailableException("Connection refused"));

        processor.process(identity);

        verify(lifecycle).fail(identity, "CONVERSATION_OLLAMA_UNAVAILABLE");
    }
}
