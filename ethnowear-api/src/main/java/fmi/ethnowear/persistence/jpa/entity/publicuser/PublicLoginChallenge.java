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
@Table(name = "PublicLoginChallenges", schema = "ethnowear")
public class PublicLoginChallenge extends AppendOnlyEntity {

    @Column(name = "NonceHash", nullable = false, length = 64, updatable = false)
    private String nonceHash;

    @Column(name = "ExpiresAt", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "ConsumedAt")
    private LocalDateTime consumedAt;

    public PublicLoginChallenge(String nonceHash, LocalDateTime expiresAt) {
        this.nonceHash = nonceHash;
        this.expiresAt = expiresAt;
    }

    public void consume(LocalDateTime now) {
        this.consumedAt = now;
    }
}
