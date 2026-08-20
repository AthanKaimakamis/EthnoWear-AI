package fmi.ethnowear.persistence.jpa.entity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Embeddable
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleId implements Serializable {


    @Column(name = "UserId")
    private Long userId;

    @Column(name = "RoleId")
    private Long roleId;
}
