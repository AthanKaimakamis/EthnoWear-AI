package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.model.conversation.ConversationTurnQueuedEvent;
import fmi.ethnowear.application.port.conversation.ConversationTurnProcessor;
import fmi.ethnowear.application.service.conversation.turn.ConversationTurnQueuedListener;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class ConversationTurnQueuedListenerTest {
    private final ConversationTurnProcessor processor = mock(ConversationTurnProcessor.class);
    private final ConversationTurnQueuedEvent event = new ConversationTurnQueuedEvent(UUID.randomUUID(), UUID.randomUUID());

    @Test
    void dispatchesToBoundedExecutor() {
        var listener = new ConversationTurnQueuedListener(processor, Runnable::run);
        listener.queued(event);
        verify(processor).process(event);
    }

    @Test
    void rejectedDispatchLeavesCallerSuccessful() {
        Executor rejecting = command -> { throw new RejectedExecutionException(); };
        var listener = new ConversationTurnQueuedListener(processor, rejecting);
        assertThatCode(() -> listener.queued(event)).doesNotThrowAnyException();
        verifyNoInteractions(processor);
    }

    @Test
    void processorFailureDoesNotEscapeAsyncBoundary() {
        doThrow(new IllegalStateException("failure")).when(processor).process(event);
        var listener = new ConversationTurnQueuedListener(processor, Runnable::run);
        assertThatCode(() -> listener.queued(event)).doesNotThrowAnyException();
    }
}
