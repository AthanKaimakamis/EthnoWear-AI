package fmi.ethnowear.persistence.jpa.entity.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "UserRoles", schema = "ethnowear")
public class UserRole {

    @EmbeddedId
    private UserRoleId id = new UserRoleId();

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    @MapsId("roleId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "RoleId", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AssignedByUserId")
    private User assignedByUser;

    @CreationTimestamp
    @Column(name = "AssignedAt", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    public UserRole(
            User user,
            Role role,
            User assignedByUser
    ) {
        this.user = user;
        this.role = role;
        this.assignedByUser = assignedByUser;
    }
}
