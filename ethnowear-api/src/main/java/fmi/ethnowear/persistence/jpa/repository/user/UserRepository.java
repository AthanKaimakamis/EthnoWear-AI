package fmi.ethnowear.persistence.jpa.repository.user;

import fmi.ethnowear.persistence.jpa.entity.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNormalizedUsernameAndDeletedAtIsNull(String normalizedUsername);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByNormalizedUsername(String normalizedUsername);

    @Query("""
            SELECT user
            FROM User user
            LEFT JOIN UserInfo info ON info.user = user
            WHERE user.deletedAt IS NULL
              AND (
                   :search IS NULL
                   OR LOWER(user.username) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(info.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(info.lastName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(info.email) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<User> search(
            @Param("search") String search,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT user
            FROM User user
            WHERE user.enabled = true
              AND user.deletedAt IS NULL
              AND EXISTS (
                  SELECT assignment.id
                  FROM UserRole assignment
                  WHERE assignment.user = user
                    AND assignment.role.name =
                        fmi.ethnowear.domain.model.user.RoleName.ADMINISTRATOR
              )
            """)
    List<User> lockEnabledAdministrators();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT user
        FROM User user
        WHERE user.normalizedUsername = :normalizedUsername
          AND user.deletedAt IS NULL
        """)
    Optional<User> findForAuthenticationUpdate(
            @Param("normalizedUsername") String normalizedUsername
    );
}
