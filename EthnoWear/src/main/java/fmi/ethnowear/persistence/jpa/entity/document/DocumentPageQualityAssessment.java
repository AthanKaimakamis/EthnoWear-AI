package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.domain.model.document.quality.AssessmentType;
import fmi.ethnowear.domain.model.document.quality.AssessorType;
import fmi.ethnowear.domain.model.document.quality.QualityStatus;
import fmi.ethnowear.persistence.jpa.entity.UpdatableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "DocumentPageQualityAssessments", schema = "ethnowear")
public class DocumentPageQualityAssessment extends UpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentPageId", nullable = false)
    private DocumentPage documentPage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DocumentPageMediaId")
    private DocumentPageMedia documentPageMedia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProcessingJobId")
    private DocumentProcessingJob processingJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DocumentPageOcrResultId")
    private DocumentPageOcrResult documentPageOcrResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "AssessmentType", nullable = false, length = 50)
    private AssessmentType assessmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "AssessorType", nullable = false, length = 50)
    private AssessorType assessorType;

    @Column(name = "AssessorName", length = 100)
    private String assessorName;

    @Column(name = "AssessorVersion", length = 100)
    private String assessorVersion;

    @Column(name = "ScoreVersion", nullable = false, length = 50)
    private String scoreVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "QualityStatus", nullable = false, length = 50)
    private QualityStatus qualityStatus = QualityStatus.INCOMPLETE;

    @Column(name = "OverallScore", precision = 5, scale = 4)
    private BigDecimal overallScore;

    @Column(name = "Summary", length = 1000)
    private String summary;

    @Column(name = "Limitations", length = 1000)
    private String limitations;

    @Column(name = "IsCurrent", nullable = false)
    private boolean current = true;
}
