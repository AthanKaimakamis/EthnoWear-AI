package fmi.ethnowear.persistence.jpa.repository.publicuser;

import fmi.ethnowear.persistence.jpa.entity.publicuser.PublicUserSession;
import org.springframework.data.jpa.repository.*;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PublicUserSessionRepository extends JpaRepository<PublicUserSession, Long> {

    @EntityGraph(attributePaths = "publicUser")
    Optional<PublicUserSession> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
            String tokenHash, LocalDateTime now
    );

    @Modifying
    @Query("update PublicUserSession s set s.revokedAt = :now where s.tokenHash = :hash and s.revokedAt is null")
    int revoke(String hash, LocalDateTime now);
}
