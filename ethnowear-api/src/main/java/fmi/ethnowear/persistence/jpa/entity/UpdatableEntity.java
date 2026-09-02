package fmi.ethnowear.persistence.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@MappedSuperclass
@Getter
@NoArgsConstructor
public abstract class UpdatableEntity extends AppendOnlyEntity {

    @UpdateTimestamp(source = SourceType.DB)
    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;
}
