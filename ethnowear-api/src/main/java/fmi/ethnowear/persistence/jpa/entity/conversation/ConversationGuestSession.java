package fmi.ethnowear.persistence.jpa.entity.conversation;

import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.type.SqlServerRowVersionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Type;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "ConversationGuestSessions", schema = "ethnowear")
public class ConversationGuestSession extends UpdatableEntity  {

    @Column(name = "TokenHash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Setter
    @Column(name = "ExpiresAt", nullable = false)
    private LocalDateTime expiresAt;

    @Setter
    @Column(name = "RevokedAt")
    private LocalDateTime revokedAt;

    @Getter(AccessLevel.NONE)
    @Version
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Type(SqlServerRowVersionType.class)
    @Column(
            name = "RowVersion",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "binary(8)"
    )
    private byte[] rowVersion;

    public ConversationGuestSession(String tokenHash, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public byte[] getRowVersion() {
        return rowVersion == null ? null : rowVersion.clone();
    }
}
