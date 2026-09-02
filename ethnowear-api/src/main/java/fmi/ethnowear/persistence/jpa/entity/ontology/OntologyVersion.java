package fmi.ethnowear.persistence.jpa.entity.ontology;

import fmi.ethnowear.domain.model.ontology.OntologyVersionStatus;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.persistence.jpa.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "OntologyVersions", schema = "ethnowear")
public class OntologyVersion extends AppendOnlyEntity {

    @Column(name = "VersionNumber", nullable = false, updatable = false)
    private long versionNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PreviousVersionId", updatable = false)
    private OntologyVersion previousVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RestoredFromVersionId", updatable = false)
    private OntologyVersion restoredFromVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CreatedByUserId", updatable = false)
    private User createdByUser;

    @Column(name = "ChangeReason", nullable = false, length = 500, updatable = false)
    private String changeReason;

    @Column(name = "OntologyContent", nullable = false, updatable = false, columnDefinition = "nvarchar(max)")
    private String ontologyContent;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ContentHash", nullable = false, length = 64, updatable = false, columnDefinition = "char(64)")
    private String contentHash;

    @Column(name = "FileName", nullable = false, length = 260, updatable = false)
    private String fileName;

    @Column(name = "OntologyNamespace", nullable = false, length = 500, updatable = false)
    private String ontologyNamespace;

    @Column(name = "IsValid", nullable = false, updatable = false)
    private boolean valid;

    @Column(name = "ValidationMessage", length = 2000)
    private String validationMessage;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "Status", nullable = false, length = 20)
    private OntologyVersionStatus status;

    public OntologyVersion(
            long versionNumber,
            OntologyVersion previousVersion,
            OntologyVersion restoredFromVersion,
            User createdByUser,
            String changeReason,
            String ontologyContent,
            String contentHash,
            String fileName,
            String ontologyNamespace,
            boolean valid,
            String validationMessage,
            OntologyVersionStatus status
    ) {
        this.versionNumber = versionNumber;
        this.previousVersion = previousVersion;
        this.restoredFromVersion = restoredFromVersion;
        this.createdByUser = createdByUser;
        this.changeReason = changeReason;
        this.ontologyContent = ontologyContent;
        this.contentHash = contentHash;
        this.fileName = fileName;
        this.ontologyNamespace = ontologyNamespace;
        this.valid = valid;
        this.validationMessage = validationMessage;
        this.status = status;
    }

    public void supersede() {
        if (status != OntologyVersionStatus.ACTIVE)
            throw new IllegalStateException("Only the active ontology version can be superseded");

        status = OntologyVersionStatus.SUPERSEDED;
    }

    public void activate() {
        if (status != OntologyVersionStatus.STAGED)
            throw new IllegalStateException("Only a staged ontology version can be activated");

        status = OntologyVersionStatus.ACTIVE;
        validationMessage = null;
    }

    public void failActivation(String message) {
        if (status != OntologyVersionStatus.STAGED)
            throw new IllegalStateException("Only a staged ontology version can fail activation");

        status = OntologyVersionStatus.FAILED;
        validationMessage = message;
    }
}
