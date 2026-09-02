package fmi.ethnowear.application.service.conversation.access;

import fmi.ethnowear.application.model.conversation.ConversationOwner;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationGuestSession;
import fmi.ethnowear.persistence.jpa.repository.conversation.ConversationGuestSessionRepository;
import fmi.ethnowear.util.ContentHashUtils;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationGuestSessionService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration TTL = Duration.ofDays(7);

    private final ConversationGuestSessionRepository repository;
    private final Clock clock;

    @Transactional
    public IssuedSession create() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(TTL);
        var session = repository.saveAndFlush(new ConversationGuestSession(
                ContentHashUtils.sha256(token),
                LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)
        ));
        return new IssuedSession(ConversationOwner.forGuest(session.getId()), token, expiresAt);
    }

    @Transactional
    public IssuedSession obtain(String token) {
        if (token != null && token.matches("[A-Za-z0-9_-]{43}")) {
            var existing = repository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                    ContentHashUtils.sha256(token),
                    LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
            );

            if (existing.isPresent()) {
                var session = existing.get();
                return new IssuedSession(
                        ConversationOwner.forGuest(session.getId()),
                        token,
                        session.getExpiresAt().toInstant(ZoneOffset.UTC)
                );
            }
        }

        return create();
    }

    public Optional<ConversationOwner> resolve(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}"))
            return Optional.empty();

        return repository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                ContentHashUtils.sha256(token),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        ).map(session -> ConversationOwner.forGuest(session.getId()));
    }


    public record IssuedSession(ConversationOwner owner, String token, Instant expiresAt) {
        @Override
        public @NonNull String toString() {
            return "IssuedSession[token=REDACTED]";
        }
    }
}
