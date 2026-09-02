package fmi.ethnowear.persistence.jpa.entity.publicuser;

import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.type.SqlServerRowVersionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Type;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "PublicUsers", schema = "ethnowear")
public class PublicUser extends UpdatableEntity {

    @Column(name = "PublicId", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "DisplayName", nullable = false, length = 200)
    private String displayName;

    @Column(name = "Email", nullable = false, length = 320)
    private String email;

    @Column(name = "Enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "LastLoginAt", nullable = false)
    private LocalDateTime lastLoginAt;

    @Getter(AccessLevel.NONE)
    @Version
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Type(SqlServerRowVersionType.class)
    @Column(name = "RowVersion", nullable = false, insertable = false,
            updatable = false, columnDefinition = "binary(8)")
    private byte[] rowVersion;

    public PublicUser(String displayName, String email, LocalDateTime now) {
        this.publicId = UUID.randomUUID();
        recordLogin(displayName, email, now);
    }

    public void recordLogin(String displayName, String email, LocalDateTime now) {
        this.displayName = displayName;
        this.email = email;
        this.lastLoginAt = now;
    }

    public byte[] getRowVersion() {
        return rowVersion == null ? null : rowVersion.clone();
    }
}
