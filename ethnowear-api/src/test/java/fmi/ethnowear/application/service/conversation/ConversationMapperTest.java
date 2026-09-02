package fmi.ethnowear.application.service.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;
import fmi.ethnowear.persistence.jpa.entity.conversation.Conversation;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurn;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurnEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationMapperTest {

    private final ConversationMapper mapper = new ConversationMapper(new ObjectMapper());

    @Test
    void mapsPersistedHeaderWithUtcTimestampsAndPublicIdentity() {
        var conversation = mock(Conversation.class);
        UUID publicId = UUID.randomUUID();
        when(conversation.getPublicId()).thenReturn(publicId);
        when(conversation.getTitle()).thenReturn("Embroidery");
        when(conversation.getLanguage()).thenReturn("bg");
        when(conversation.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 8, 31, 12, 0));
        when(conversation.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 8, 31, 12, 1));

        var details = mapper.toDetails(conversation);

        assertEquals(publicId, details.conversationId());
        assertEquals("Embroidery", details.title());
        assertEquals("bg", details.language());
        assertEquals(Instant.parse("2026-08-31T12:00:00Z"), details.createdAt());
        assertEquals(Instant.parse("2026-08-31T12:01:00Z"), details.updatedAt());
        verify(conversation, never()).getId();
        verify(conversation, never()).getPublicUser();
        verify(conversation, never()).getGuestSession();
        verify(conversation, never()).getRowVersion();
    }

    @Test
    void preservesMissingTitle() {
        var conversation = mock(Conversation.class);
        when(conversation.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 8, 31, 12, 0));
        when(conversation.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 8, 31, 12, 0));
        assertNull(mapper.toDetails(conversation).title());
    }

    @Test
    void acceptanceSupportsEveryPersistedStatus() {
        var conversation = mock(Conversation.class);
        var turn = mock(ConversationTurn.class);
        UUID conversationId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        when(conversation.getPublicId()).thenReturn(conversationId);
        when(turn.getConversation()).thenReturn(conversation);
        when(turn.getPublicId()).thenReturn(turnId);
        for (var status : ConversationTurnStatus.values()) {
            when(turn.getStatus()).thenReturn(status);
            var details = mapper.toAccepted(turn);
            assertEquals(conversationId, details.conversationId());
            assertEquals(turnId, details.turnId());
            assertEquals(status, details.status());
        }
        verify(turn, never()).getId();
        verify(turn, never()).getRequestHash();
    }

    @Test
    void replayUsesHistoricalEventInsteadOfLatestTurnStatus() {
        var conversation = mock(Conversation.class);
        var turn = mock(ConversationTurn.class);
        var event = mock(ConversationTurnEvent.class);
        UUID conversationId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        when(turn.getConversation()).thenReturn(conversation);
        when(conversation.getPublicId()).thenReturn(conversationId);
        when(turn.getPublicId()).thenReturn(turnId);
        when(turn.getStatus()).thenReturn(ConversationTurnStatus.COMPLETED);
        when(event.getTurn()).thenReturn(turn);
        when(event.getEventId()).thenReturn(2L);
        when(event.getStatus()).thenReturn(ConversationTurnStatus.RUNNING);
        when(event.getStage()).thenReturn(ConversationProgressStage.READING_ONTOLOGY);
        when(event.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 8, 31, 12, 0));

        var progress = mapper.toProgress(event);

        assertEquals(2, progress.eventId());
        assertEquals(conversationId, progress.conversationId());
        assertEquals(turnId, progress.turnId());
        assertEquals(ConversationTurnStatus.RUNNING, progress.status());
        assertEquals(ConversationProgressStage.READING_ONTOLOGY, progress.stage());
        assertEquals(Instant.parse("2026-08-31T12:00:00Z"), progress.occurredAt());
        assertNull(progress.errorCode());
        verify(turn, never()).getStatus();
        verify(turn, never()).getStage();
        verify(turn, never()).getErrorCode();
    }

    @Test
    void failedEventPreservesSafeCodeAndNullStage() {
        var turn = mock(ConversationTurn.class);
        var event = mock(ConversationTurnEvent.class);
        when(turn.getConversation()).thenReturn(mock(Conversation.class));
        when(event.getTurn()).thenReturn(turn);
        when(event.getStatus()).thenReturn(ConversationTurnStatus.FAILED);
        when(event.getErrorCode()).thenReturn("GENERATION_TIMEOUT");
        when(event.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 8, 31, 12, 0));

        var details = mapper.toProgress(event);

        assertEquals(ConversationTurnStatus.FAILED, details.status());
        assertNull(details.stage());
        assertEquals("GENERATION_TIMEOUT", details.errorCode());
    }
}
