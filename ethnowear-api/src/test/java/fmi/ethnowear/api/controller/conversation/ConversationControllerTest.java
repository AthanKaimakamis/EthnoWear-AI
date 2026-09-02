package fmi.ethnowear.api.controller.conversation;

import fmi.ethnowear.application.dto.conversation.*;
import fmi.ethnowear.application.exception.ConversationException;
import fmi.ethnowear.application.model.conversation.ConversationOwner;
import fmi.ethnowear.application.service.conversation.*;
import fmi.ethnowear.application.service.conversation.access.ConversationOwnerResolver;
import fmi.ethnowear.infrastructure.security.conversation.ConversationGuestCookies;
import fmi.ethnowear.infrastructure.sse.ConversationEventBroadcaster;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationControllerTest {
    private final ConversationService service = mock(ConversationService.class);
    private final ConversationOwnerResolver owners = mock(ConversationOwnerResolver.class);
    private final ConversationGuestCookies cookies = mock(ConversationGuestCookies.class);
    private final ConversationEventBroadcaster events = mock(ConversationEventBroadcaster.class);
    private final ConversationAvailabilityService availability = mock(ConversationAvailabilityService.class);
    private final ConversationController controller = new ConversationController(service, owners, cookies, events, availability);

    @Test
    void createsConversationForResolvedOwner() {
        var request = new MockHttpServletRequest();
        var owner = ConversationOwner.forGuest(4);
        var command = new ConversationCreateCommand(UUID.randomUUID(), "bg");
        var details = new ConversationDetails(UUID.randomUUID(), null, "bg", Instant.now(), Instant.now());
        when(cookies.read(request)).thenReturn("token");
        when(owners.resolve(null, "token")).thenReturn(Optional.of(owner));
        when(service.create(owner, command)).thenReturn(details);

        var response = controller.create(null, request, command);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/conversations/" + details.conversationId());
        assertThat(response.getBody()).isSameAs(details);
    }

    @Test
    void rejectsRequestWithoutOwner() {
        var request = new MockHttpServletRequest();
        var command = new ConversationCreateCommand(UUID.randomUUID(), "bg");
        when(owners.resolve(null, null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.create(null, request, command))
                .isInstanceOf(ConversationException.class)
                .extracting("code").isEqualTo("CONVERSATION_OWNER_REQUIRED");
        verifyNoInteractions(service);
    }

    @Test
    void acceptsPersistedTurnAndReturnsItsLocation() {
        var request = new MockHttpServletRequest();
        var owner = ConversationOwner.forGuest(6);
        UUID conversationId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        var command = new ConversationMessageCommand(UUID.randomUUID(), "Въпрос");
        var accepted = new ConversationTurnAcceptedDetails(
                conversationId,
                turnId,
                fmi.ethnowear.domain.model.conversation.ConversationTurnStatus.QUEUED
        );
        when(owners.resolve(null, null)).thenReturn(Optional.of(owner));
        when(service.submit(owner, conversationId, command)).thenReturn(accepted);

        var response = controller.submit(null, request, conversationId, command);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getHeaders().getLocation()).hasToString(
                "/api/conversations/" + conversationId + "/turns/" + turnId
        );
        assertThat(response.getBody()).isSameAs(accepted);
    }

    @Test
    void renamesAndDeletesForResolvedOwner() {
        var request = new MockHttpServletRequest();
        var owner = ConversationOwner.forGuest(6);
        UUID conversationId = UUID.randomUUID();
        var command = new ConversationRenameCommand("New title");
        var details = new ConversationDetails(conversationId, "New title", "bg", Instant.now(), Instant.now());
        when(owners.resolve(null, null)).thenReturn(Optional.of(owner));
        when(service.rename(owner, conversationId, command)).thenReturn(details);

        assertThat(controller.rename(null, request, conversationId, command)).isSameAs(details);
        controller.delete(null, request, conversationId);

        verify(service).delete(owner, conversationId);
    }
}
