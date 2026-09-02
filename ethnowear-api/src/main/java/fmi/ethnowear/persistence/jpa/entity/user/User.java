package fmi.ethnowear.persistence.jpa.entity.user;

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
@NoArgsConstructor
@Entity
@Table(name = "Users", schema = "ethnowear")
public class User extends UpdatableEntity {

    @Column(name = "Username", nullable = false, length = 100)
    private String username;

    @Setter(AccessLevel.NONE)
    @Column(
            name = "NormalizedUsername",
            insertable = false,
            updatable = false,
            length = 100
    )
    private String normalizedUsername;

    @Column(name = "PasswordHash", length = 255)
    private String passwordHash;

    @Column(name = "MustChangePassword", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "TemporaryPasswordExpiresAt")
    private LocalDateTime temporaryPasswordExpiresAt;

    @Column(name = "Enabled", nullable = false)
    private boolean enabled;

    @Column(name = "FailedLoginAttempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "LockedUntil")
    private LocalDateTime lockedUntil;

    @Column(name = "TokenVersion", nullable = false)
    private int tokenVersion;

    @Column(name = "LastLoginAt")
    private LocalDateTime lastLoginAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CreatedByUserId")
    private User createdByUser;

    @Column(name = "DeletedAt")
    private LocalDateTime deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DeletedByUserId")
    private User deletedByUser;

    @Version
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Type(SqlServerRowVersionType.class)
    @Setter(AccessLevel.NONE)
    @Column(
            name = "RowVersion",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "binary(8)"
    )
    private byte[] rowVersion;

    public User(String username, User createdByUser) {
        this.username = username;
        this.createdByUser = createdByUser;
    }

    public void assignPassword(
            String passwordHash,
            boolean mustChangePassword,
            LocalDateTime temporaryPasswordExpiresAt
    ) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = mustChangePassword;
        this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
        this.tokenVersion++;
    }

    public void completePasswordChange(String passwordHash) {
        assignPassword(passwordHash, false, null);
        unlock();
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
        this.tokenVersion++;
    }

    public void unlock() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void recordSuccessfulLogin(LocalDateTime loginAt) {
        this.lastLoginAt = loginAt;
        unlock();
    }

    public void recordFailedLogin(LocalDateTime lockedUntil) {
        this.failedLoginAttempts++;
        this.lockedUntil = lockedUntil;
    }

    public void revokeTokens() {
        this.tokenVersion++;
    }

    public void softDelete(User deletedByUser, LocalDateTime deletedAt) {
        this.enabled = false;
        this.passwordHash = null;
        this.mustChangePassword = true;
        this.temporaryPasswordExpiresAt = null;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.deletedByUser = deletedByUser;
        this.deletedAt = deletedAt;
        revokeTokens();
    }
}
