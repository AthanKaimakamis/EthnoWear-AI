package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.domain.model.document.*;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
