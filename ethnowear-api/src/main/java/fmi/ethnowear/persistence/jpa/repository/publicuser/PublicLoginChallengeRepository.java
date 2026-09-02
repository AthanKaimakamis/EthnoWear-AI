package fmi.ethnowear.persistence.jpa.repository.publicuser;

import fmi.ethnowear.persistence.jpa.entity.publicuser.PublicLoginChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

import java.util.Optional;

public interface PublicLoginChallengeRepository extends JpaRepository<PublicLoginChallenge, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PublicLoginChallenge> findByNonceHash(String nonceHash);
}
