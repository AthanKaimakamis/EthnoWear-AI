package fmi.ethnowear.persistence.jpa.entity.conversation;

import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.type.SqlServerRowVersionType;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Type;
import org.hibernate.generator.EventType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConversationGuestSessionTest {

    @Test
    void mapsTableAndInheritedIdentityTimestamps() {
        Table table = ConversationGuestSession.class.getAnnotation(Table.class);
        assertEquals("ethnowear", table.schema());
        assertEquals("ConversationGuestSessions", table.name());
        assertEquals(UpdatableEntity.class, ConversationGuestSession.class.getSuperclass());
    }

    @Test
    void tokenHashIsImmutableAfterPersistence() throws Exception {
        Column column = ConversationGuestSession.class.getDeclaredField("tokenHash").getAnnotation(Column.class);
        assertEquals("TokenHash", column.name());
        assertEquals(64, column.length());
        assertFalse(column.nullable());
        assertFalse(column.updatable());
        assertThrows(NoSuchMethodException.class,
                () -> ConversationGuestSession.class.getMethod("setTokenHash", String.class));
    }

    @Test
    void allowsExpiryAndRevocationUpdatesWithoutChangingIdentity() throws Exception {
        LocalDateTime expiry = LocalDateTime.of(2026, 9, 1, 12, 0);
        var session = new ConversationGuestSession("a".repeat(64), expiry);
        assertEquals("a".repeat(64), session.getTokenHash());
        assertEquals(expiry, session.getExpiresAt());
        assertNull(session.getRevokedAt());
        session.setExpiresAt(expiry.plusDays(1));
        session.setRevokedAt(expiry.minusHours(1));
        assertEquals(expiry.plusDays(1), session.getExpiresAt());
        assertEquals(expiry.minusHours(1), session.getRevokedAt());
        assertFalse(ConversationGuestSession.class.getDeclaredField("expiresAt").getAnnotation(Column.class).nullable());
        assertTrue(ConversationGuestSession.class.getDeclaredField("revokedAt").getAnnotation(Column.class).nullable());
    }

    @Test
    void versionIsDatabaseGeneratedAndNotWritable() throws Exception {
        var field = ConversationGuestSession.class.getDeclaredField("rowVersion");
        assertNotNull(field.getAnnotation(Version.class));
        assertEquals(SqlServerRowVersionType.class, field.getAnnotation(Type.class).value());
        assertEquals(Set.of(EventType.INSERT, EventType.UPDATE), Set.of(field.getAnnotation(Generated.class).event()));
        Column column = field.getAnnotation(Column.class);
        assertEquals("RowVersion", column.name());
        assertEquals("binary(8)", column.columnDefinition());
        assertFalse(column.nullable());
        assertFalse(column.insertable());
        assertFalse(column.updatable());
        assertThrows(NoSuchMethodException.class,
                () -> ConversationGuestSession.class.getMethod("setRowVersion", byte[].class));
    }

    @Test
    void versionGetterReturnsDefensiveCopy() throws Exception {
        var session = new ConversationGuestSession("a".repeat(64), LocalDateTime.of(2026, 9, 1, 12, 0));
        assertNull(session.getRowVersion());
        var field = ConversationGuestSession.class.getDeclaredField("rowVersion");
        field.setAccessible(true);
        byte[] version = {0, 0, 0, 0, 0, 0, 0, 1};
        field.set(session, version);
        byte[] exposed = session.getRowVersion();
        assertNotSame(version, exposed);
        exposed[7] = 2;
        assertArrayEquals(version, session.getRowVersion());
    }
}
