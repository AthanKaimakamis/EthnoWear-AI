package fmi.ethnowear.persistence.jpa.entity.publicuser;

import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Immutable
@Entity
@Table(name = "PublicUserIdentities", schema = "ethnowear")
public class PublicUserIdentity extends AppendOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PublicUserId", nullable = false, updatable = false)
    private PublicUser publicUser;

    @Column(name = "Issuer", nullable = false, length = 100, updatable = false)
    private String issuer;

    @Column(name = "Subject", nullable = false, length = 255, updatable = false)
    private String subject;

    public PublicUserIdentity(PublicUser publicUser, String issuer, String subject) {
        this.publicUser = publicUser;
        this.issuer = issuer;
        this.subject = subject;
    }
}
