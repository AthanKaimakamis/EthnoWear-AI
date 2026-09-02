package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.model.conversation.ConversationTurnQueuedEvent;
import fmi.ethnowear.application.service.conversation.turn.ConversationTurnLifecycleService;
import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;
import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import fmi.ethnowear.persistence.jpa.entity.conversation.*;
import fmi.ethnowear.persistence.jpa.repository.conversation.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConversationTurnLifecycleServiceTest {
    private final ConversationRepository conversations = mock(ConversationRepository.class);
    private final ConversationTurnRepository turns = mock(ConversationTurnRepository.class);
    private final ConversationTurnEventRepository events = mock(ConversationTurnEventRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final ConversationMapper mapper = mock(ConversationMapper.class);
    private final Instant now = Instant.parse("2026-09-01T12:00:00Z");
    private final ConversationTurnLifecycleService service = new ConversationTurnLifecycleService(
            conversations,
            turns,
            events,
            publisher,
            mapper,
            Clock.fixed(now, ZoneOffset.UTC)
    );
    private final UUID conversationId = UUID.randomUUID();
    private final UUID turnId = UUID.randomUUID();

    @Test
    void queuedTurnIsClaimedAndEmitsEvent() {
        var conversation = mock(Conversation.class);
        var turn = mock(ConversationTurn.class);
        when(conversation.getId()).thenReturn(2L);
        when(conversation.getPublicId()).thenReturn(conversationId);
        when(conversation.getLanguage()).thenReturn("bg");
        when(turn.getPublicId()).thenReturn(turnId);
        when(turn.getUserMessage()).thenReturn("Въпрос");
        when(turn.getStatus()).thenReturn(ConversationTurnStatus.QUEUED);
        when(conversations.findByPublicId(conversationId)).thenReturn(Optional.of(conversation));
        when(turns.findForUpdate(2, turnId)).thenReturn(Optional.of(turn));

        var context = service.start(new ConversationTurnQueuedEvent(conversationId, turnId)).orElseThrow();

        assertThat(context.userMessage()).isEqualTo("Въпрос");
        verify(turn).start(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        verify(events).saveAndFlush(any(ConversationTurnEvent.class));
    }

    @Test
    void runningTurnIsNotClaimedAgain() {
        var conversation = mock(Conversation.class);
        var turn = mock(ConversationTurn.class);
        when(conversation.getId()).thenReturn(2L);
        when(conversations.findByPublicId(conversationId)).thenReturn(Optional.of(conversation));
        when(turns.findForUpdate(2, turnId)).thenReturn(Optional.of(turn));
        when(turn.getStatus()).thenReturn(ConversationTurnStatus.RUNNING);

        assertThat(service.start(new ConversationTurnQueuedEvent(conversationId, turnId))).isEmpty();
        verify(turn, never()).start(any());
        verifyNoInteractions(events);
    }

    @Test
    void progressTransitionIsPersistedAsEvent() {
        var turn = lockedTurn(ConversationTurnStatus.RUNNING);
        when(turn.getStage()).thenReturn(ConversationProgressStage.RECEIVED);

        assertThat(service.advance(
                new ConversationTurnQueuedEvent(conversationId, turnId),
                ConversationProgressStage.READING_ONTOLOGY
        )).isTrue();
        verify(turn).advance(ConversationProgressStage.READING_ONTOLOGY);
        verify(events).saveAndFlush(any(ConversationTurnEvent.class));
    }

    @Test
    void completionAndFailureUseSafeTerminalTransitions() {
        var completedTurn = lockedTurn(ConversationTurnStatus.RUNNING);
        assertThat(service.complete(
                new ConversationTurnQueuedEvent(conversationId, turnId),
                "{\"answer\":\"text\"}"
        )).isTrue();
        verify(completedTurn).complete(anyString(), any());

        reset(conversations, turns, events);
        var failedTurn = lockedTurn(ConversationTurnStatus.RUNNING);
        when(failedTurn.isActive()).thenReturn(true);
        assertThat(service.fail(
                new ConversationTurnQueuedEvent(conversationId, turnId),
                "GENERATION_FAILED"
        )).isTrue();
        verify(failedTurn).fail(eq("GENERATION_FAILED"), any());
    }

    private ConversationTurn lockedTurn(ConversationTurnStatus status) {
        var conversation = mock(Conversation.class);
        var turn = mock(ConversationTurn.class);
        when(conversation.getId()).thenReturn(2L);
        when(conversations.findByPublicId(conversationId)).thenReturn(Optional.of(conversation));
        when(turns.findForUpdate(2, turnId)).thenReturn(Optional.of(turn));
        when(turn.getStatus()).thenReturn(status);
        return turn;
    }
}
