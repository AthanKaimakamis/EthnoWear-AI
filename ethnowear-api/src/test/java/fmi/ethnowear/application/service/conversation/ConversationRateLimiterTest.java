package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.exception.ConversationException;
import fmi.ethnowear.application.model.conversation.ConversationOwner;
import fmi.ethnowear.application.service.conversation.policy.ConversationRateLimiter;
import fmi.ethnowear.config.ConversationSecurityProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationRateLimiterTest {

    @Test
    void limitsEachOwnerIndependently() {
        var limiter = new ConversationRateLimiter(new ConversationSecurityProperties(2, 100));
        var first = ConversationOwner.forGuest(1);
        var second = ConversationOwner.forGuest(2);

        assertThatCode(() -> {
            limiter.acquire(first);
            limiter.acquire(first);
            limiter.acquire(second);
        }).doesNotThrowAnyException();

        assertThatThrownBy(() -> limiter.acquire(first))
                .isInstanceOf(ConversationException.class)
                .extracting("code")
                .isEqualTo("CONVERSATION_RATE_LIMITED");
    }
}
