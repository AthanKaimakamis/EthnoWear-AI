package fmi.ethnowear.persistence.jpa.entity.conversation;

import fmi.ethnowear.domain.model.conversation.ConversationEvidenceType;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationTurnEvidenceTest {

    private ConversationTurn turn() {
        var guest = new ConversationGuestSession("a".repeat(64), LocalDateTime.of(2026, 9, 2, 12, 0));
        var conversation = new Conversation(null, guest, UUID.randomUUID(), "bg");
        return new ConversationTurn(conversation, UUID.randomUUID(), 1, "Question");
    }

    @Test
    void preservesExactPrivateSnapshotForEachEvidenceType() {
        var turn = turn();
        String snapshot = "{\"documentId\":1,\"pageIds\":[2],\"printedPageNumber\":\"111\"}";
        for (var type : ConversationEvidenceType.values()) {
            var evidence = new ConversationTurnEvidence(turn, "evidence:1", type, snapshot);
            assertSame(turn, evidence.getTurn());
            assertEquals(type, evidence.getEvidenceType());
            assertEquals("evidence:1", evidence.getEvidenceKey());
            assertEquals(snapshot, evidence.getSnapshotJson());
        }
    }

    @Test
    void rejectsInvalidEvidenceKeys() {
        for (String key : new String[]{null, "", "  ", "x".repeat(101)})
            assertThrows(IllegalArgumentException.class,
                    () -> new ConversationTurnEvidence(turn(), key, ConversationEvidenceType.DOCUMENT, "{}"));
    }

    @Test
    void rejectsEmptyOrOversizedSnapshots() {
        for (String snapshot : new String[]{null, "", "  ", "x".repeat(16385)})
            assertThrows(IllegalArgumentException.class,
                    () -> new ConversationTurnEvidence(turn(), "key", ConversationEvidenceType.DOCUMENT, snapshot));
    }

    @Test
    void acceptsExactSqlLengthBoundaries() {
        String snapshot = "{\"text\":\"" + "x".repeat(16373) + "\"}";
        assertEquals(16384, snapshot.length());
        assertDoesNotThrow(() -> new ConversationTurnEvidence(
                turn(), "x".repeat(100), ConversationEvidenceType.DOCUMENT, snapshot));
    }

    @Test
    void requiresTurnAndEvidenceType() {
        assertThrows(NullPointerException.class,
                () -> new ConversationTurnEvidence(null, "key", ConversationEvidenceType.ARCHIVE, "{}"));
        assertThrows(NullPointerException.class,
                () -> new ConversationTurnEvidence(turn(), "key", null, "{}"));
    }

    @Test
    void mapsImmutablePrivateHistoryWithoutCascades() throws Exception {
        Class<ConversationTurnEvidence> type = ConversationTurnEvidence.class;
        assertNotNull(type.getAnnotation(Immutable.class));
        Table table = type.getAnnotation(Table.class);
        assertEquals("ethnowear", table.schema());
        assertEquals("ConversationTurnEvidence", table.name());
        var field = type.getDeclaredField("turn");
        ManyToOne relation = field.getAnnotation(ManyToOne.class);
        assertEquals(FetchType.LAZY, relation.fetch());
        assertFalse(relation.optional());
        assertEquals(0, relation.cascade().length);
        JoinColumn join = field.getAnnotation(JoinColumn.class);
        assertEquals("ConversationTurnId", join.name());
        assertFalse(join.nullable());
        assertFalse(join.updatable());
        for (String name : new String[]{"evidenceKey", "evidenceType", "snapshotJson"}) {
            Column column = type.getDeclaredField(name).getAnnotation(Column.class);
            assertFalse(column.nullable());
            assertFalse(column.updatable());
        }
        assertEquals(100, type.getDeclaredField("evidenceKey").getAnnotation(Column.class).length());
        assertEquals(20, type.getDeclaredField("evidenceType").getAnnotation(Column.class).length());
        assertEquals("nvarchar(max)", type.getDeclaredField("snapshotJson").getAnnotation(Column.class).columnDefinition());
    }
}
