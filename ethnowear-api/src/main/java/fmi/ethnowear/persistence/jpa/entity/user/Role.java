package fmi.ethnowear.persistence.jpa.entity.user;

import fmi.ethnowear.domain.model.user.RoleName;
import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.type.SqlServerRowVersionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Type;
import org.hibernate.generator.EventType;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "Roles", schema = "ethnowear")
public class Role extends UpdatableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "Name", nullable = false, length = 50)
    private RoleName name;

    @Column(name = "Description", length = 50)
    private String description;

    @Version
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Type(SqlServerRowVersionType.class)
    @Setter(AccessLevel.NONE)
    @Column(
            name = "RowVersion",
            nullable = false,
            insertable = false,
            updatable = false,
            columnDefinition = "binary(8)"
    )
    private byte[] rowVersion;
}
