package fmi.ethnowear.persistence.jpa.entity.conversation;

import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;
import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationTurnEventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 31, 12, 0);

    private ConversationTurn turn() throws Exception {
        var guest = new ConversationGuestSession("a".repeat(64), NOW.plusDays(1));
        var conversation = new Conversation(null, guest, UUID.randomUUID(), "bg");
        var turn = new ConversationTurn(conversation, UUID.randomUUID(), 1, "Question");
        var createdAt = AppendOnlyEntity.class.getDeclaredField("createdAt");
        createdAt.setAccessible(true);
        createdAt.set(turn, NOW);
        return turn;
    }

    @Test
    void allocatesCursorAndKeepsHistoricalSnapshot() throws Exception {
        var turn = turn();
        var queued = new ConversationTurnEvent(turn);
        turn.start(NOW);
        turn.advance(ConversationProgressStage.READING_ONTOLOGY);
        var running = new ConversationTurnEvent(turn);
        assertEquals(1, queued.getEventId());
        assertEquals(2, running.getEventId());
        assertEquals(2, turn.getLastEventId());
        assertEquals(ConversationTurnStatus.QUEUED, queued.getStatus());
        assertEquals(ConversationProgressStage.RECEIVED, queued.getStage());
        assertEquals(ConversationTurnStatus.RUNNING, running.getStatus());
        assertEquals(ConversationProgressStage.READING_ONTOLOGY, running.getStage());
        assertSame(turn, running.getTurn());
    }

    @Test
    void failedSnapshotHasSafeCodeAndNoStage() throws Exception {
        var turn = turn();
        turn.fail("GENERATION_TIMEOUT", NOW);
        var event = new ConversationTurnEvent(turn);
        assertEquals(ConversationTurnStatus.FAILED, event.getStatus());
        assertEquals("GENERATION_TIMEOUT", event.getErrorCode());
        assertNull(event.getStage());
    }

    @Test
    void rejectsMissingTurn() {
        assertThrows(NullPointerException.class, () -> new ConversationTurnEvent(null));
    }

    @Test
    void isImmutableAndUsesExactSqlMapping() throws Exception {
        assertNotNull(ConversationTurnEvent.class.getAnnotation(Immutable.class));
        Table table = ConversationTurnEvent.class.getAnnotation(Table.class);
        assertEquals("ethnowear", table.schema());
        assertEquals("ConversationTurnEvents", table.name());
        var relation = ConversationTurnEvent.class.getDeclaredField("turn").getAnnotation(ManyToOne.class);
        assertEquals(FetchType.LAZY, relation.fetch());
        assertFalse(relation.optional());
        assertEquals(0, relation.cascade().length);
        for (String field : new String[]{"eventId", "status", "stage", "errorCode"})
            assertFalse(ConversationTurnEvent.class.getDeclaredField(field).getAnnotation(Column.class).updatable());
    }
}
