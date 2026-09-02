package fmi.ethnowear.persistence.jpa.entity.conversation;

import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.util.ContentHashUtils;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationTurnTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 8, 31, 12, 0);

    private Conversation conversation() {
        var guest = new ConversationGuestSession("a".repeat(64), CREATED.plusDays(1));
        return new Conversation(null, guest, UUID.randomUUID(), "bg");
    }

    private ConversationTurn persisted() throws Exception {
        var turn = new ConversationTurn(conversation(), UUID.randomUUID(), 1, "Question");
        var field = AppendOnlyEntity.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(turn, CREATED);
        return turn;
    }

    @Test
    void queuedDefaultsAndExactContentHash() {
        String text = "  \u0428\u0435\u0432\u0438\u0446\u0430?\n";
        var turn = new ConversationTurn(conversation(), UUID.randomUUID(), 1, text);
        assertNotNull(turn.getPublicId());
        assertEquals(text, turn.getUserMessage());
        assertEquals(ContentHashUtils.sha256(text), turn.getRequestHash());
        assertEquals(ConversationTurnStatus.QUEUED, turn.getStatus());
        assertEquals(ConversationProgressStage.RECEIVED, turn.getStage());
        assertTrue(turn.isActive());
        assertEquals(0, turn.getLastEventId());
        assertNull(turn.getAnswerJson());
    }

    @Test
    void validatesConstructorBounds() {
        assertThrows(IllegalArgumentException.class, () -> new ConversationTurn(conversation(), UUID.randomUUID(), 0, "Question"));
        for (String message : new String[]{null, " ", "x".repeat(1001)})
            assertThrows(IllegalArgumentException.class, () -> new ConversationTurn(conversation(), UUID.randomUUID(), 1, message));
        assertDoesNotThrow(() -> new ConversationTurn(conversation(), UUID.randomUUID(), 1, "x".repeat(1000)));
        assertThrows(NullPointerException.class, () -> new ConversationTurn(null, UUID.randomUUID(), 1, "Question"));
        assertThrows(NullPointerException.class, () -> new ConversationTurn(conversation(), null, 1, "Question"));
    }

    @Test
    void successfulLifecycleIsConsistent() throws Exception {
        var turn = persisted();
        turn.start(CREATED.plusSeconds(1));
        turn.advance(ConversationProgressStage.REASONING);
        assertEquals(ConversationTurnStatus.RUNNING, turn.getStatus());
        assertEquals(ConversationProgressStage.REASONING, turn.getStage());
        turn.complete("{\"answer\":\"Grounded answer\"}", CREATED.plusSeconds(2));
        assertEquals(ConversationTurnStatus.COMPLETED, turn.getStatus());
        assertFalse(turn.isActive());
        assertNull(turn.getStage());
        assertNull(turn.getErrorCode());
        assertEquals(CREATED.plusSeconds(1), turn.getStartedAt());
        assertEquals(CREATED.plusSeconds(2), turn.getFinishedAt());
    }

    @Test
    void repeatedCompletionDoesNotChangeSnapshot() throws Exception {
        var turn = persisted();
        turn.start(CREATED);
        turn.complete("{}", CREATED.plusSeconds(1));
        turn.complete("{}", CREATED.plusSeconds(2));
        assertEquals(CREATED.plusSeconds(1), turn.getFinishedAt());
        assertThrows(IllegalStateException.class, () -> turn.complete("{\"different\":true}", CREATED.plusSeconds(3)));
        assertThrows(IllegalStateException.class, () -> turn.cancel(CREATED.plusSeconds(3)));
        assertThrows(IllegalStateException.class, () -> turn.fail("TIMEOUT", CREATED.plusSeconds(3)));
        assertThrows(IllegalStateException.class, () -> turn.start(CREATED.plusSeconds(3)));
    }

    @Test
    void failureBeforeStartIsSafeAndIdempotent() throws Exception {
        var turn = persisted();
        turn.fail("GENERATION_TIMEOUT", CREATED.plusSeconds(1));
        turn.fail("GENERATION_TIMEOUT", CREATED.plusSeconds(2));
        assertEquals(ConversationTurnStatus.FAILED, turn.getStatus());
        assertNull(turn.getStartedAt());
        assertNull(turn.getAnswerJson());
        assertNull(turn.getStage());
        assertFalse(turn.isActive());
        assertEquals(CREATED.plusSeconds(1), turn.getFinishedAt());
        assertThrows(IllegalStateException.class, () -> turn.fail("DIFFERENT", CREATED.plusSeconds(3)));
    }

    @Test
    void runningFailurePreservesStartedAt() throws Exception {
        var turn = persisted();
        turn.start(CREATED);
        turn.fail("TIMEOUT", CREATED.plusSeconds(1));
        assertEquals(CREATED, turn.getStartedAt());
        assertEquals("TIMEOUT", turn.getErrorCode());
        assertFalse(turn.isActive());
    }

    @Test
    void cancellationBlocksLateCompletion() throws Exception {
        var turn = persisted();
        turn.cancel(CREATED.plusSeconds(1));
        turn.cancel(CREATED.plusSeconds(2));
        assertEquals(ConversationTurnStatus.CANCELLED, turn.getStatus());
        assertEquals(CREATED.plusSeconds(1), turn.getFinishedAt());
        assertFalse(turn.isActive());
        assertNull(turn.getStage());
        assertNull(turn.getAnswerJson());
        assertThrows(IllegalStateException.class, () -> turn.complete("{}", CREATED.plusSeconds(3)));
    }

    @Test
    void rejectsInvalidTimestampsBeforeMutation() throws Exception {
        var fresh = new ConversationTurn(conversation(), UUID.randomUUID(), 1, "Question");
        assertThrows(IllegalStateException.class, () -> fresh.start(CREATED));
        var turn = persisted();
        assertThrows(IllegalArgumentException.class, () -> turn.start(CREATED.minusSeconds(1)));
        assertEquals(ConversationTurnStatus.QUEUED, turn.getStatus());
        turn.start(CREATED.plusSeconds(2));
        assertThrows(IllegalArgumentException.class, () -> turn.complete("{}", CREATED.plusSeconds(1)));
        assertEquals(ConversationTurnStatus.RUNNING, turn.getStatus());
        assertNull(turn.getAnswerJson());
    }

    @Test
    void rejectsInvalidProgressAndSafeOutputBounds() throws Exception {
        var turn = persisted();
        assertThrows(IllegalStateException.class, () -> turn.advance(ConversationProgressStage.REASONING));
        turn.start(CREATED);
        assertThrows(NullPointerException.class, () -> turn.advance(null));
        for (String error : new String[]{null, "", "raw exception", "X".repeat(101)})
            assertThrows(IllegalArgumentException.class, () -> turn.fail(error, CREATED));
        for (String answer : new String[]{null, " ", "x".repeat(65537)})
            assertThrows(IllegalArgumentException.class, () -> turn.complete(answer, CREATED));
        assertEquals(ConversationTurnStatus.RUNNING, turn.getStatus());
    }

    @Test
    void eventSequenceIsMonotonicAndOverflowCannotCorruptIt() throws Exception {
        var turn = persisted();
        assertEquals(1, turn.nextEventId());
        assertEquals(2, turn.nextEventId());
        var field = ConversationTurn.class.getDeclaredField("lastEventId");
        field.setAccessible(true);
        field.setLong(turn, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, turn::nextEventId);
        assertEquals(Long.MAX_VALUE, turn.getLastEventId());
    }

    @Test
    void versionGetterIsDefensive() throws Exception {
        var turn = persisted();
        assertNull(turn.getRowVersion());
        var field = ConversationTurn.class.getDeclaredField("rowVersion");
        field.setAccessible(true);
        byte[] version = {0, 0, 0, 0, 0, 0, 0, 1};
        field.set(turn, version);
        turn.getRowVersion()[7] = 2;
        assertArrayEquals(version, turn.getRowVersion());
    }

    @Test
    void mappingUsesExactTableLazyOwnerAndImmutableInput() throws Exception {
        Table table = ConversationTurn.class.getAnnotation(Table.class);
        assertEquals("ethnowear", table.schema());
        assertEquals("ConversationTurns", table.name());
        ManyToOne relationship = ConversationTurn.class.getDeclaredField("conversation").getAnnotation(ManyToOne.class);
        assertEquals(FetchType.LAZY, relationship.fetch());
        assertFalse(relationship.optional());
        assertEquals(0, relationship.cascade().length);
        for (String name : new String[]{"publicId", "clientRequestId", "turnSequence", "userMessage", "requestHash"})
            assertFalse(ConversationTurn.class.getDeclaredField(name).getAnnotation(Column.class).updatable());
    }
}
