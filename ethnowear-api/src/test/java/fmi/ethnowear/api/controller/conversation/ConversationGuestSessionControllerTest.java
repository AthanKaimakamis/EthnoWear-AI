package fmi.ethnowear.api.controller.conversation;

import fmi.ethnowear.application.model.conversation.ConversationOwner;
import fmi.ethnowear.application.model.publicauth.PublicUserPrincipal;
import fmi.ethnowear.application.service.conversation.access.ConversationGuestSessionService;
import fmi.ethnowear.infrastructure.security.conversation.ConversationGuestCookies;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ConversationGuestSessionControllerTest {
    private final ConversationGuestSessionService sessions = mock(ConversationGuestSessionService.class);
    private final ConversationGuestCookies cookies = mock(ConversationGuestCookies.class);
    private final Instant now = Instant.parse("2026-09-01T10:00:00Z");
    private final ConversationGuestSessionController controller = new ConversationGuestSessionController(
            sessions, cookies, Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void guestGetsHttpOnlyCookieThroughCookieComponent() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var issued = new ConversationGuestSessionService.IssuedSession(
                ConversationOwner.forGuest(4), "a".repeat(43), now.plusSeconds(3600)
        );
        when(cookies.read(request)).thenReturn(null);
        when(sessions.obtain(null)).thenReturn(issued);

        assertThat(controller.obtain(null, request, response).getStatusCode().value()).isEqualTo(204);
        verify(cookies).write(response, issued.token(), Duration.ofHours(1));
    }

    @Test
    void authenticatedPublicUserDoesNotReceiveGuestSession() {
        var principal = new PublicUserPrincipal(3, UUID.randomUUID(), 8);
        assertThat(controller.obtain(principal, new MockHttpServletRequest(), new MockHttpServletResponse())
                .getStatusCode().value()).isEqualTo(204);
        verifyNoInteractions(sessions, cookies);
    }
}
