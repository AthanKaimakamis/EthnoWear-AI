package fmi.ethnowear.persistence.jpa.entity.user;

import fmi.ethnowear.persistence.jpa.type.SqlServerRowVersionType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "UserInfo", schema = "ethnowear")
public class UserInfo {

    @Id
    @Column(name = "UserId")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "UserId", nullable = false)
    private User user;

    @Column(name = "FirstName", nullable = false, length = 100)
    private String firstName;

    @Column(name = "LastName", nullable = false, length = 100)
    private String lastName;

    @Column(name = "Email", length = 320)
    private String email;

    @Setter(AccessLevel.NONE)
    @Column(name = "NormalizedEmail", insertable = false, updatable = false)
    private String normalizedEmail;

    @Column(name = "Phone", length = 50)
    private String phone;

    @Column(name = "AddressLine1", length = 250)
    private String addressLine1;

    @Column(name = "AddressLine2", length = 250)
    private String addressLine2;

    @Column(name = "City", length = 100)
    private String city;

    @Column(name = "PostalCode", length = 20)
    private String postalCode;

    @Column(name = "CountryCode", length = 2, columnDefinition = "char(2)")
    private String countryCode;

    @CreationTimestamp
    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

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

    public UserInfo(User user) {
        this.user = user;
    }

    public void update(
            String firstName,
            String lastName,
            String email,
            String phone,
            String addressLine1,
            String addressLine2,
            String city,
            String postalCode,
            String countryCode
    ) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
    }
}
