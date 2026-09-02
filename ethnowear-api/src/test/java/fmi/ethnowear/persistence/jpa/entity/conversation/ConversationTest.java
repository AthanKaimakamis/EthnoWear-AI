package fmi.ethnowear.persistence.jpa.entity.conversation;

import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.entity.publicuser.PublicUser;
import fmi.ethnowear.persistence.jpa.type.SqlServerRowVersionType;
import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Type;
import org.hibernate.generator.EventType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ConversationTest {

    private ConversationGuestSession guest() {
        return new ConversationGuestSession("a".repeat(64), LocalDateTime.of(2026, 9, 2, 12, 0));
    }

    @Test
    void acceptsGuestOwnership() {
        var guest = guest();
        UUID request = UUID.randomUUID();
        var conversation = new Conversation(null, guest, request, "bg");
        assertSame(guest, conversation.getGuestSession());
        assertNull(conversation.getPublicUser());
        assertEquals(request, conversation.getClientRequestId());
        assertEquals("bg", conversation.getLanguage());
        assertNotNull(conversation.getPublicId());
        assertNull(conversation.getTitle());
    }

    @Test
    void acceptsUserOwnership() {
        PublicUser user = mock(PublicUser.class);
        var conversation = new Conversation(user, null, UUID.randomUUID(), "en");
        assertSame(user, conversation.getPublicUser());
        assertNull(conversation.getGuestSession());
    }

    @Test
    void rejectsMissingOrAmbiguousOwnership() {
        assertThrows(IllegalArgumentException.class,
                () -> new Conversation(null, null, UUID.randomUUID(), "bg"));
        assertThrows(IllegalArgumentException.class,
                () -> new Conversation(mock(PublicUser.class), guest(), UUID.randomUUID(), "bg"));
    }

    @Test
    void rejectsMissingRequestIdentityOrLanguage() {
        assertThrows(NullPointerException.class, () -> new Conversation(null, guest(), null, "bg"));
        assertThrows(NullPointerException.class,
                () -> new Conversation(null, guest(), UUID.randomUUID(), null));
    }

    @Test
    void titleUpdatePreservesRequestAndPublicIdentity() {
        var conversation = new Conversation(null, guest(), UUID.randomUUID(), "bg");
        UUID publicId = conversation.getPublicId();
        UUID requestId = conversation.getClientRequestId();
        conversation.setTitle("Embroidery question");
        assertEquals("Embroidery question", conversation.getTitle());
        assertEquals(publicId, conversation.getPublicId());
        assertEquals(requestId, conversation.getClientRequestId());
    }

    @Test
    void mapsSqlTableAndImmutableColumns() throws Exception {
        Table table = Conversation.class.getAnnotation(Table.class);
        assertEquals("ethnowear", table.schema());
        assertEquals("Conversations", table.name());
        assertEquals(UpdatableEntity.class, Conversation.class.getSuperclass());
        for (String name : new String[]{"publicId", "clientRequestId", "language"}) {
            Column column = Conversation.class.getDeclaredField(name).getAnnotation(Column.class);
            assertEquals(Character.toUpperCase(name.charAt(0)) + name.substring(1), column.name());
            assertFalse(column.nullable());
            assertFalse(column.updatable());
        }
        assertEquals(2, Conversation.class.getDeclaredField("language").getAnnotation(Column.class).length());
        assertEquals(300, Conversation.class.getDeclaredField("title").getAnnotation(Column.class).length());
    }

    @Test
    void ownershipRelationsAreLazyImmutableAndDoNotCascade() throws Exception {
        for (String name : new String[]{"publicUser", "guestSession"}) {
            var field = Conversation.class.getDeclaredField(name);
            ManyToOne relation = field.getAnnotation(ManyToOne.class);
            assertEquals(FetchType.LAZY, relation.fetch());
            assertEquals(0, relation.cascade().length);
            assertTrue(relation.optional());
            JoinColumn join = field.getAnnotation(JoinColumn.class);
            assertEquals(name.equals("publicUser") ? "PublicUserId" : "GuestSessionId", join.name());
            assertFalse(join.updatable());
            assertTrue(join.nullable());
        }
    }

    @Test
    void versionIsSqlManagedAndDefensivelyExposed() throws Exception {
        var field = Conversation.class.getDeclaredField("rowVersion");
        assertNotNull(field.getAnnotation(Version.class));
        assertEquals(SqlServerRowVersionType.class, field.getAnnotation(Type.class).value());
        assertEquals(Set.of(EventType.INSERT, EventType.UPDATE), Set.of(field.getAnnotation(Generated.class).event()));
        Column column = field.getAnnotation(Column.class);
        assertEquals("RowVersion", column.name());
        assertEquals("binary(8)", column.columnDefinition());
        assertFalse(column.nullable());
        assertFalse(column.insertable());
        assertFalse(column.updatable());
        var conversation = new Conversation(null, guest(), UUID.randomUUID(), "bg");
        assertNull(conversation.getRowVersion());
        byte[] version = {0, 0, 0, 0, 0, 0, 0, 1};
        field.setAccessible(true);
        field.set(conversation, version);
        conversation.getRowVersion()[7] = 2;
        assertArrayEquals(version, conversation.getRowVersion());
    }
}
