package fmi.ethnowear.persistence.jpa.entity.publicuser;

import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "PublicUserSessions", schema = "ethnowear")
public class PublicUserSession extends AppendOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PublicUserId", nullable = false, updatable = false)
    private PublicUser publicUser;

    @Column(name = "TokenHash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "ExpiresAt", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "RevokedAt")
    private LocalDateTime revokedAt;

    public PublicUserSession(PublicUser publicUser, String tokenHash, LocalDateTime expiresAt) {
        this.publicUser = publicUser;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
}
