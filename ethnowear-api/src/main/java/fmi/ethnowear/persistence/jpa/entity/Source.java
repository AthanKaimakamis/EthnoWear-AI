package fmi.ethnowear.persistence.jpa.entity;

import fmi.ethnowear.domain.model.archive.SourceType;
import fmi.ethnowear.domain.model.rights.RightsStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "Sources", schema = "ethnowear")
public class Source extends UpdatableEntity {

    @Column(name = "Title", nullable = false)
    private String title;

    @Column(name = "Author")
    private String author;

    @Column(name = "Publisher")
    private String publisher;

    @Column(name = "PublicationYear")
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "SourceType", nullable = false)
    private SourceType sourceType;

    @Column(name = "Language")
    private String language;

    @Column(name = "FilePath")
    private String filePath;

    @Column(name = "Url")
    private String url;

    @Column(name = "Isbn")
    private String isbn;

    @Lob
    @Column(name = "Notes")
    private String notes;

    @Column(name = "IsTrusted", nullable = false)
    private boolean trusted;

    @Enumerated(EnumType.STRING)
    @Setter(AccessLevel.NONE)
    @Column(name = "RightsStatus", nullable = false, length = 30)
    private RightsStatus rightsStatus = RightsStatus.UNKNOWN;

    @Setter(AccessLevel.NONE)
    @Column(name = "License", length = 500)
    private String license;

    @Setter(AccessLevel.NONE)
    @Column(name = "PublicDisplayAllowed", nullable = false)
    private boolean publicDisplayAllowed;

    public void updateRights(
            RightsStatus rightsStatus,
            String license,
            boolean publicDisplayAllowed
    ) {
        RightsStatus normalizedStatus = rightsStatus == null
                ? RightsStatus.UNKNOWN
                : rightsStatus;
        String normalizedLicense = normalizeLicense(license);

        if (normalizedStatus.requiresLicense() && normalizedLicense == null)
            throw new IllegalArgumentException("License is required for licensed content");

        if (publicDisplayAllowed && !normalizedStatus.permitsPublicDisplay())
            throw new IllegalArgumentException("Rights status does not permit public display");

        this.rightsStatus = normalizedStatus;
        this.license = normalizedLicense;
        this.publicDisplayAllowed = publicDisplayAllowed;
    }

    private String normalizeLicense(String value) {
        if (value == null || value.isBlank())
            return null;

        String normalized = value.trim();

        if (normalized.length() > 500)
            throw new IllegalArgumentException("License cannot exceed 500 characters");

        return normalized;
    }

}
