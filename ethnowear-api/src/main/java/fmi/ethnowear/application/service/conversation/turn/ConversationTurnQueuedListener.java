package fmi.ethnowear.application.service.conversation.turn;

import fmi.ethnowear.application.model.conversation.ConversationTurnQueuedEvent;
import fmi.ethnowear.application.port.conversation.ConversationTurnProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

import java.util.concurrent.*;

@Slf4j
@Component
@ConditionalOnBean(ConversationTurnProcessor.class)
public class ConversationTurnQueuedListener {

    private final ConversationTurnProcessor processor;

    private final Executor executor;

    public ConversationTurnQueuedListener(
            ConversationTurnProcessor processor,
            @Qualifier("conversationTaskExecutor") Executor executor
    ) {
        this.processor = processor;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void queued(ConversationTurnQueuedEvent event) {
        try {
            executor.execute(() -> process(event));
        } catch(RejectedExecutionException exception) {
            log.warn(
                    "Conversation executor is full; turn {} remains queued",
                    event.turnId()
            );
        }
    }

    private void process(ConversationTurnQueuedEvent event) {
        try {
            processor.process(event);
        } catch(RuntimeException exception) {
            log.error(
                    "Unexpected conversation processing failure for turn {}",
                    event.turnId(),
                    exception
            );
        }
    }
}
