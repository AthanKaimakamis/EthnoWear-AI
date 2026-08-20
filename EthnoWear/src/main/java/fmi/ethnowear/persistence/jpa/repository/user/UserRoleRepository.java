package fmi.ethnowear.persistence.jpa.repository.user;

import fmi.ethnowear.domain.model.user.RoleName;
import fmi.ethnowear.persistence.jpa.entity.user.UserRole;
import fmi.ethnowear.persistence.jpa.entity.user.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("""
            SELECT assignment
            FROM UserRole assignment
            JOIN FETCH assignment.role
            WHERE assignment.user.id = :userId
            ORDER BY assignment.role.name
            """)
    List<UserRole> findWithRolesByUserId(
            @Param("userId") Long userId
    );

    @Query("""
            SELECT assignment
            FROM UserRole assignment
            JOIN FETCH assignment.role
            WHERE assignment.user.id IN :userIds
            ORDER BY assignment.user.id, assignment.role.name
            """)
    List<UserRole> findWithRolesByUserIds(
            @Param("userIds") Collection<Long> userIds
    );

    @Query("""
            SELECT assignment
            FROM UserRole assignment
            JOIN FETCH assignment.role
            WHERE assignment.user.id = :userId
              AND assignment.role.name = :roleName
            """)
    Optional<UserRole> findByUserIdAndRoleName(
            @Param("userId") Long userId,
            @Param("roleName") RoleName roleName
    );

    boolean existsByUser_IdAndRole_Name(
            Long userId,
            RoleName roleName
    );

    long countByUser_Id(Long userId);
}
