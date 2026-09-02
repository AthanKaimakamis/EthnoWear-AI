package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.service.conversation.access.ConversationGuestSessionService;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationGuestSession;
import fmi.ethnowear.persistence.jpa.repository.conversation.ConversationGuestSessionRepository;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationGuestSessionServiceTest {
    private final ConversationGuestSessionRepository repository = mock(ConversationGuestSessionRepository.class);
    private final Instant now = Instant.parse("2026-09-01T08:00:00Z");
    private final ConversationGuestSessionService service = new ConversationGuestSessionService(
            repository,
            Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void createPersistsOnlyTokenHashAndReturnsOwner() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            var session = (ConversationGuestSession) invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 42L);
            return session;
        });

        var issued = service.create();

        assertThat(issued.token()).hasSize(43);
        assertThat(issued.owner().guestSessionId()).isEqualTo(42L);
        assertThat(issued.expiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
        verify(repository).saveAndFlush(argThat(session -> session.getTokenHash()
                .equals(ContentHashUtils.sha256(issued.token()))));
        assertThat(issued.toString()).doesNotContain(issued.token());
    }

    @Test
    void resolveUsesHashAndRejectsMalformedTokens() {
        String token = "a".repeat(43);
        var session = new ConversationGuestSession("hash", LocalDateTime.now());
        ReflectionTestUtils.setField(session, "id", 7L);
        when(repository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                eq(ContentHashUtils.sha256(token)), any()
        )).thenReturn(Optional.of(session));

        assertThat(service.resolve(token).orElseThrow().guestSessionId()).isEqualTo(7L);
        assertThat(service.resolve("invalid")).isEmpty();
        assertThat(service.resolve(null)).isEmpty();
        verify(repository, times(1)).findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(anyString(), any());
    }

    @Test
    void obtainReusesValidSessionWithoutPersistingAnother() {
        String token = "a".repeat(43);
        var expiry = LocalDateTime.ofInstant(now.plus(Duration.ofDays(2)), ZoneOffset.UTC);
        var session = new ConversationGuestSession(ContentHashUtils.sha256(token), expiry);
        ReflectionTestUtils.setField(session, "id", 9L);
        when(repository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(session));

        var issued = service.obtain(token);

        assertThat(issued.token()).isEqualTo(token);
        assertThat(issued.owner().guestSessionId()).isEqualTo(9L);
        assertThat(issued.expiresAt()).isEqualTo(expiry.toInstant(ZoneOffset.UTC));
        verify(repository, never()).saveAndFlush(any());
    }
}
