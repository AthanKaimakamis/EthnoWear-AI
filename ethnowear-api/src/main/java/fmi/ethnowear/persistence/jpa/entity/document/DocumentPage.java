package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.domain.model.document.*;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.quality.QualityStatus;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.type.SqlServerRowVersionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Type;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "DocumentPages", schema = "ethnowear",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_DocumentPages_DocumentId_PageSequence",
                columnNames = {"DocumentId", "PageSequence"}
        )
)
public class DocumentPage extends UpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentId", nullable = false)
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceReferenceId")
    private SourceReference sourceReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "PageKind", nullable = false, length = 50)
    private PageKind pageKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "PageRole", nullable = false, length = 50)
    private PageRole pageRole = PageRole.NORMAL;

    @Column(name = "PageSequence", nullable = false)
    private Integer pageSequence;

    @Column(name = "PdfPageIndex")
    private Integer pdfPageIndex;

    @Column(name = "PrintedPageNumber", length = 50)
    private String printedPageNumber;

    @Column(name = "PrintedPageSort")
    private Integer printedPageSort;

    @Column(name = "PageLabel", length = 100)
    private String pageLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "ProvenanceStatus", nullable = false, length = 50)
    private ProvenanceStatus provenanceStatus = ProvenanceStatus.UNKNOWN_SOURCE;

    @Lob
    @Column(name = "RawOcrText")
    private String rawOcrText;

    @Lob
    @Column(name = "CorrectedText")
    private String correctedText;

    @Column(name = "CorrectedTextHash", length = 128)
    private String correctedTextHash;

    @Column(name = "OcrEngine", length = 100)
    private String ocrEngine;

    @Column(name = "OcrEngineVersion", length = 100)
    private String ocrEngineVersion;

    @Column(name = "OcrLanguage", length = 20)
    private String ocrLanguage;

    @Column(name = "OcrConfidence", precision = 5, scale = 4)
    private BigDecimal ocrConfidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CurrentQualityAssessmentId")
    private DocumentPageQualityAssessment currentQualityAssessment;

    @Column(name = "CurrentQualityScore", precision = 5, scale = 4)
    private BigDecimal currentQualityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "CurrentQualityStatus", length = 50)
    private QualityStatus currentQualityStatus;

    @Column(name = "CurrentQualityPassedChecks")
    private Integer currentQualityPassedChecks;

    @Column(name = "CurrentQualityFailedChecks")
    private Integer currentQualityFailedChecks;

    @Column(name = "CurrentQualityAssessedAt")
    private LocalDateTime currentQualityAssessedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ProcessingState", nullable = false, length = 50)
    private ProcessingState processingState = ProcessingState.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "ReviewState", nullable = false, length = 50)
    private ReviewState reviewState = ReviewState.NOT_READY;

    @Enumerated(EnumType.STRING)
    @Column(name = "TranscriptionApprovalState", nullable = false, length = 50)
    private TranscriptionApprovalState transcriptionApprovalState = TranscriptionApprovalState.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "ProvenanceTrustState", nullable = false, length = 50)
    private ProvenanceTrustState provenanceTrustState = ProvenanceTrustState.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "IndexingState", nullable = false, length = 50)
    private IndexingState indexingState = IndexingState.NOT_ELIGIBLE;

    @Column(name = "Reviewer", length = 150)
    private String reviewer;

    @Column(name = "ReviewedAt")
    private LocalDateTime reviewedAt;

    @Lob
    @Column(name = "ReviewNotes")
    private String reviewNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "EvidenceState", nullable = false, length = 50)
    private EvidenceState evidenceState = EvidenceState.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CanonicalDocumentPageId")
    private DocumentPage canonicalDocumentPage;

    @Lob
    @Column(name = "ProvenanceNote")
    private String provenanceNote;

    @Column(name = "ProvenanceReviewedBy", length = 150)
    private String provenanceReviewedBy;

    @Column(name = "ProvenanceReviewedAt")
    private LocalDateTime provenanceReviewedAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "RetiredAt")
    private LocalDateTime retiredAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "RetiredBy", length = 150)
    private String retiredBy;

    @Setter(AccessLevel.NONE)
    @Column(name = "RetirementReason", length = 500)
    private String retirementReason;

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

    public void retire(
            LocalDateTime retiredAt,
            String retiredBy,
            String retirementReason
    ) {
        if (evidenceState == EvidenceState.RETIRED)
            return;

        this.retiredAt = java.util.Objects.requireNonNull(
                retiredAt,
                "Retirement time is required"
        );
        this.retiredBy = requireRetirementText(retiredBy, "Retiring user", 150);
        this.retirementReason = requireRetirementText(
                retirementReason,
                "Retirement reason",
                500
        );
        evidenceState = EvidenceState.RETIRED;
    }

    private String requireRetirementText(
            String value,
            String field,
            int maximumLength
    ) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " is required");

        String normalized = value.trim();

        if (normalized.length() > maximumLength)
            throw new IllegalArgumentException(
                    field + " cannot exceed " + maximumLength + " characters"
            );

        return normalized;
    }
}
