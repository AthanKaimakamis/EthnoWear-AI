package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "DocumentPageFigureCandidates", schema = "ethnowear")
public class DocumentPageFigureCandidate extends AppendOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentPageOcrResultId", nullable = false, updatable = false)
    private DocumentPageOcrResult documentPageOcrResult;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentPageMediaId", nullable = false, updatable = false)
    private DocumentPageMedia documentPageMedia;

    @Column(name = "CandidateOrdinal", nullable = false, updatable = false)
    private Integer candidateOrdinal;

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

    @Column(name = "DetectionConfidence", precision = 5, scale = 4, updatable = false)
    private BigDecimal detectionConfidence;
}
