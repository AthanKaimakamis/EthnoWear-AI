package fmi.ethnowear.persistence.jpa.entity.conversation;

import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.entity.publicuser.PublicUser;
import fmi.ethnowear.persistence.jpa.type.SqlServerRowVersionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Type;
import org.hibernate.generator.EventType;
import java.util.Objects;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "Conversations", schema = "ethnowear")
public class Conversation extends UpdatableEntity {

    @Column(name = "PublicId", nullable = false, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PublicUserId", updatable = false)
    private PublicUser publicUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "GuestSessionId", updatable = false)
    private ConversationGuestSession guestSession;

    @Column(name = "ClientRequestId", nullable = false, updatable = false)
    private UUID clientRequestId;

    @Column(name = "Language", nullable = false, length = 2, updatable = false)
    private String language;

    @Setter
    @Column(name = "Title", length = 300)
    private String title;

    @Getter(AccessLevel.NONE)
    @Version
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Type(SqlServerRowVersionType.class)
    @Column(name = "RowVersion", nullable = false,
            insertable = false, updatable = false, columnDefinition = "binary(8)")
    private byte[] rowVersion;

    public Conversation(
            PublicUser publicUser,
            ConversationGuestSession guestSession,
            UUID clientRequestId,
            String language
    ) {
        if ((publicUser == null) == (guestSession == null))
            throw new IllegalArgumentException("Exactly one conversation owner is required");

        this.publicId = UUID.randomUUID();
        this.publicUser = publicUser;
        this.guestSession = guestSession;
        this.clientRequestId = Objects.requireNonNull(clientRequestId);
        this.language = Objects.requireNonNull(language);
    }

    public byte[] getRowVersion() {
        return rowVersion == null ? null : rowVersion.clone();
    }
}