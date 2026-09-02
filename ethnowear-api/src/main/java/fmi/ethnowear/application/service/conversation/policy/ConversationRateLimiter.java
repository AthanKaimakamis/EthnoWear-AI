package fmi.ethnowear.application.service.conversation.policy;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fmi.ethnowear.application.exception.ConversationException;
import fmi.ethnowear.application.model.conversation.ConversationOwner;
import fmi.ethnowear.config.ConversationSecurityProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ConversationRateLimiter {

    private final ConversationSecurityProperties properties;
    private final Cache<String, AtomicInteger> requests;

    public ConversationRateLimiter(ConversationSecurityProperties properties) {
        this.properties = properties;
        this.requests = Caffeine.newBuilder()
                .maximumSize(properties.maximumTrackedOwners())
                .expireAfterWrite(Duration.ofMinutes(1))
                .build();
    }

    public void acquire(@NonNull ConversationOwner owner) {
        String key = owner.isPublicUser()
                ? "user:" + owner.publicUserId()
                : "guest:" + owner.guestSessionId();

        int count = requests
                .get(key, ignored -> new AtomicInteger())
                .incrementAndGet();

        if (count > properties.maximumTurnsPerMinute())
            throw new ConversationException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "CONVERSATION_RATE_LIMITED",
                    "Too many conversation requests. Please retry shortly"
            );
    }
}