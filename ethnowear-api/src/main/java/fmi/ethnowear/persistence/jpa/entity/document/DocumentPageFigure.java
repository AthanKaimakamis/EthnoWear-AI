package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import fmi.ethnowear.persistence.jpa.type.SqlServerRowVersionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Type;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "DocumentPageFigures", schema = "ethnowear")
public class DocumentPageFigure extends UpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentPageId", nullable = false, updatable = false)
    private DocumentPage documentPage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentPageMediaId", nullable = false, updatable = false)
    private DocumentPageMedia documentPageMedia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MediaAssetId", nullable = false, updatable = false)
    private MediaAsset mediaAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SourceReferenceId")
    private SourceReference sourceReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "FigureCandidateId", nullable = false, updatable = false)
    private DocumentPageFigureCandidate figureCandidate;

    @Column(name = "FigureOrdinal", nullable = false, updatable = false)
    private Integer figureOrdinal;

    @Column(name = "PrintedFigureNumber", length = 100)
    private String printedFigureNumber;

    @Column(name = "NormalizedX", nullable = false, precision = 8, scale = 7, updatable = false)
    private BigDecimal normalizedX;

    @Column(name = "NormalizedY", nullable = false, precision = 8, scale = 7, updatable = false)
    private BigDecimal normalizedY;

    @Column(name = "NormalizedWidth", nullable = false, precision = 8, scale = 7, updatable = false)
    private BigDecimal normalizedWidth;

    @Column(name = "NormalizedHeight", nullable = false, precision = 8, scale = 7, updatable = false)
    private BigDecimal normalizedHeight;

    @Column(name = "RawCaptionText", length = 2000, updatable = false)
    private String rawCaptionText;

    @Column(name = "CorrectedCaptionText", length = 2000)
    private String correctedCaptionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "ReviewState", nullable = false, length = 50)
    private FigureReviewState reviewState = FigureReviewState.PENDING;

    @Column(name = "ReviewedBy", length = 150)
    private String reviewedBy;

    @Column(name = "ReviewedAt")
    private LocalDateTime reviewedAt;

    @Column(name = "ReviewReason", length = 500)
    private String reviewReason;

    @Column(name = "DetectionConfidence", precision = 5, scale = 4, updatable = false)
    private BigDecimal detectionConfidence;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ProcessingJobId", nullable = false, updatable = false)
    private DocumentProcessingJob processingJob;

    @Column(name = "ProducingAttempt", nullable = false, updatable = false)
    private Integer producingAttempt;

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
