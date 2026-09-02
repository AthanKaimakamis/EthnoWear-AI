package fmi.ethnowear.persistence.jpa.repository.publicuser;

import fmi.ethnowear.persistence.jpa.entity.publicuser.PublicUserIdentity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublicUserIdentityRepository extends JpaRepository<PublicUserIdentity, Long> {

    @EntityGraph(attributePaths = "publicUser")
    Optional<PublicUserIdentity> findByIssuerAndSubject(String issuer, String subject);
}
