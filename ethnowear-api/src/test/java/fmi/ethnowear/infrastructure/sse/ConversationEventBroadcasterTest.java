package fmi.ethnowear.infrastructure.sse;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationEventBroadcasterTest {

    @Test
    void replayQueryAndMappingRunInsideReadOnlyTransaction() throws Exception {
        Transactional transactional = ConversationEventBroadcaster.class
                .getMethod(
                        "subscribe",
                        fmi.ethnowear.application.model.conversation.ConversationProgressStreamTarget.class,
                        long.class
                )
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }
}
