package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.dto.conversation.*;
import fmi.ethnowear.application.model.conversation.ConversationOwner;
import fmi.ethnowear.application.service.conversation.policy.ConversationRateLimiter;
import fmi.ethnowear.application.service.conversation.turn.ConversationTurnLifecycleService;
import fmi.ethnowear.persistence.jpa.entity.conversation.Conversation;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationGuestSession;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurn;
import fmi.ethnowear.persistence.jpa.repository.conversation.*;
import fmi.ethnowear.persistence.jpa.repository.publicuser.PublicUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;
import org.springframework.data.domain.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationServiceTest {
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final PublicUserRepository users = mock(PublicUserRepository.class);
    private final ConversationGuestSessionRepository guests = mock(ConversationGuestSessionRepository.class);
    private final ConversationTurnRepository turns = mock(ConversationTurnRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ConversationTurnEventRepository turnEvents = mock(ConversationTurnEventRepository.class);
    private final ConversationTurnEvidenceRepository turnEvidence = mock(ConversationTurnEvidenceRepository.class);
    private final ConversationTurnLifecycleService lifecycle = mock(ConversationTurnLifecycleService.class);
    private final ConversationRateLimiter rateLimiter = mock(ConversationRateLimiter.class);
    private final ConversationMapper mapper = mock(ConversationMapper.class);
    private final ConversationService service = new ConversationService(
            conversations,
            users,
            guests,
            turns,
            events,
            turnEvents,
            turnEvidence,
            lifecycle,
            rateLimiter,
            mapper
    );
    private final UUID requestId = UUID.randomUUID();
    private final ConversationCreateCommand command = new ConversationCreateCommand(requestId, "bg");

    @Test
    void repeatedPublicUserRequestReturnsExistingConversation() {
        var owner = ConversationOwner.forPublicUser(12);
        var existing = mock(Conversation.class);
        var details = mock(ConversationDetails.class);
        when(conversations.findByClientRequestIdAndPublicUser_Id(requestId, 12)).thenReturn(Optional.of(existing));
        when(mapper.toDetails(existing)).thenReturn(details);

        assertThat(service.create(owner, command)).isSameAs(details);
        verify(conversations, never()).saveAndFlush(any());
        verifyNoInteractions(guests);
    }

    @Test
    void guestCreationUsesOnlyGuestOwner() {
        var owner = ConversationOwner.forGuest(8);
        var guest = mock(ConversationGuestSession.class);
        var saved = mock(Conversation.class);
        var details = mock(ConversationDetails.class);
        when(conversations.findByClientRequestIdAndGuestSession_Id(requestId, 8)).thenReturn(Optional.empty());
        when(guests.getReferenceById(8L)).thenReturn(guest);
        when(conversations.saveAndFlush(any())).thenReturn(saved);
        when(mapper.toDetails(saved)).thenReturn(details);

        assertThat(service.create(owner, command)).isSameAs(details);
        verify(conversations).saveAndFlush(argThat(c -> c.getGuestSession() == guest && c.getPublicUser() == null));
        verifyNoInteractions(users);
    }

    @Test
    void publicUserDetailLookupIsOwnerScoped() {
        var owner = ConversationOwner.forPublicUser(11);
        UUID conversationId = UUID.randomUUID();
        var conversation = mock(Conversation.class);
        var details = mock(ConversationDetails.class);
        when(conversations.findByPublicIdAndPublicUser_Id(conversationId, 11))
                .thenReturn(Optional.of(conversation));
        when(mapper.toDetails(conversation)).thenReturn(details);

        assertThat(service.get(owner, conversationId)).isSameAs(details);
        verify(conversations, never()).findByPublicIdAndGuestSession_Id(any(), anyLong());
    }

    @Test
    void renamesOwnedConversationWithTrimmedTitle() {
        var owner = ConversationOwner.forGuest(5);
        UUID conversationId = UUID.randomUUID();
        var conversation = mock(Conversation.class);
        var details = mock(ConversationDetails.class);
        when(conversation.getId()).thenReturn(41L);
        when(conversations.findByPublicIdAndGuestSession_Id(conversationId, 5)).thenReturn(Optional.of(conversation));
        when(conversations.findForUpdate(41L)).thenReturn(Optional.of(conversation));
        when(conversations.saveAndFlush(conversation)).thenReturn(conversation);
        when(mapper.toDetails(conversation)).thenReturn(details);

        assertThat(service.rename(owner, conversationId, new ConversationRenameCommand("  New title  "))).isSameAs(details);
        verify(conversation).setTitle("New title");
    }

    @Test
    void deletesOwnedTerminalConversationInDependencyOrder() {
        var owner = ConversationOwner.forPublicUser(11);
        UUID conversationId = UUID.randomUUID();
        var conversation = mock(Conversation.class);
        when(conversation.getId()).thenReturn(44L);
        when(conversations.findByPublicIdAndPublicUser_Id(conversationId, 11)).thenReturn(Optional.of(conversation));
        when(conversations.findForUpdate(44L)).thenReturn(Optional.of(conversation));

        service.delete(owner, conversationId);

        var ordered = inOrder(turnEvents, turnEvidence, turns, conversations);
        ordered.verify(turnEvents).deleteByConversationId(44L);
        ordered.verify(turnEvidence).deleteByConversationId(44L);
        ordered.verify(turns).deleteByConversation_Id(44L);
        ordered.verify(conversations).delete(conversation);
        ordered.verify(conversations).flush();
    }

    @Test
    void rejectsDeletingConversationWithActiveTurn() {
        var owner = ConversationOwner.forGuest(5);
        UUID conversationId = UUID.randomUUID();
        var conversation = mock(Conversation.class);
        when(conversation.getId()).thenReturn(44L);
        when(conversations.findByPublicIdAndGuestSession_Id(conversationId, 5)).thenReturn(Optional.of(conversation));
        when(conversations.findForUpdate(44L)).thenReturn(Optional.of(conversation));
        when(turns.existsByConversation_IdAndActiveTrue(44L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(owner, conversationId))
                .extracting("code").isEqualTo("CONVERSATION_TURN_ACTIVE");
        verify(conversations, never()).delete(any());
    }

    @Test
    void hidesAnotherOwnersConversationForRenameAndDelete() {
        UUID conversationId = UUID.randomUUID();
        var owner = ConversationOwner.forPublicUser(17);
        when(conversations.findByPublicIdAndPublicUser_Id(conversationId, 17)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rename(owner, conversationId, new ConversationRenameCommand("Title")))
                .isInstanceOfSatisfying(fmi.ethnowear.application.exception.ConversationException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("CONVERSATION_NOT_FOUND");
                });
        assertThatThrownBy(() -> service.delete(owner, conversationId))
                .isInstanceOfSatisfying(fmi.ethnowear.application.exception.ConversationException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("CONVERSATION_NOT_FOUND");
                });

        verify(conversations, never()).findByPublicId(conversationId);
        verify(conversations, never()).findForUpdate(anyLong());
        verify(conversations, never()).delete(any());
    }

    @Test
    void guestListIsBoundedAndOrderedByLatestUpdate() {
        var owner = ConversationOwner.forGuest(5);
        when(conversations.findByGuestSession_Id(eq(5L), any()))
                .thenReturn(Page.empty());

        service.list(owner, PageRequest.of(1, 20));

        verify(conversations).findByGuestSession_Id(eq(5L), argThat(pageable ->
                pageable.getPageNumber() == 1
                        && pageable.getSort().getOrderFor("updatedAt").isDescending()
        ));
    }

    @Test
    void submitAllocatesNextSequenceUnderConversationLock() {
        var owner = ConversationOwner.forGuest(5);
        UUID conversationId = UUID.randomUUID();
        var conversation = mock(Conversation.class);
        var previous = mock(ConversationTurn.class);
        var accepted = new ConversationTurnAcceptedDetails(
                conversationId,
                UUID.randomUUID(),
                fmi.ethnowear.domain.model.conversation.ConversationTurnStatus.QUEUED
        );
        var message = new ConversationMessageCommand(UUID.randomUUID(), "Въпрос");
        when(conversation.getId()).thenReturn(20L);
        when(previous.getTurnSequence()).thenReturn(3L);
        when(conversations.findByPublicIdAndGuestSession_Id(conversationId, 5)).thenReturn(Optional.of(conversation));
        when(conversations.findForUpdate(20)).thenReturn(Optional.of(conversation));
        when(turns.findByConversation_IdAndClientRequestId(20, message.clientRequestId())).thenReturn(Optional.empty());
        when(turns.findFirstByConversation_IdOrderByTurnSequenceDesc(20)).thenReturn(Optional.of(previous));
        when(turns.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toAccepted(any())).thenReturn(accepted);

        assertThat(service.submit(owner, conversationId, message)).isSameAs(accepted);
        verify(turns).saveAndFlush(argThat(turn -> turn.getTurnSequence() == 4 && turn.getUserMessage().equals("Въпрос")));
        verify(events).publishEvent(any(fmi.ethnowear.application.model.conversation.ConversationTurnQueuedEvent.class));
    }

    @Test
    void submitRejectsSecondActiveTurn() {
        var owner = ConversationOwner.forPublicUser(2);
        UUID conversationId = UUID.randomUUID();
        var conversation = mock(Conversation.class);
        var message = new ConversationMessageCommand(UUID.randomUUID(), "Question");
        when(conversation.getId()).thenReturn(30L);
        when(conversations.findByPublicIdAndPublicUser_Id(conversationId, 2)).thenReturn(Optional.of(conversation));
        when(conversations.findForUpdate(30)).thenReturn(Optional.of(conversation));
        when(turns.existsByConversation_IdAndActiveTrue(30)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(owner, conversationId, message))
                .extracting("code").isEqualTo("CONVERSATION_TURN_ACTIVE");
        verify(turns, never()).saveAndFlush(any());
    }
}
