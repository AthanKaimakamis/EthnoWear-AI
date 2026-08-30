package fmi.ethnowear.persistence.jpa.repository.user;

import fmi.ethnowear.persistence.jpa.entity.user.UserInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserInfoRepository extends JpaRepository<UserInfo, Long> {

    Optional<UserInfo> findByUserId(Long userId);

    List<UserInfo> findAllByUserIdIn(Collection<Long> userIds);

    boolean existsByNormalizedEmail(String normalizedEmail);

    boolean existsByNormalizedEmailAndUserIdNot(
            String normalizedEmail,
            Long userId
    );
}
