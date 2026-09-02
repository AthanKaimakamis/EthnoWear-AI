package fmi.ethnowear.persistence.jpa.repository.publicuser;

import fmi.ethnowear.persistence.jpa.entity.publicuser.PublicUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicUserRepository extends JpaRepository<PublicUser, Long> {
}
