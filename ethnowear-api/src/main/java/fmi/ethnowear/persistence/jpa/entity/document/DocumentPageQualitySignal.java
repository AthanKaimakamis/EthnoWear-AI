package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.domain.model.document.quality.QualitySignalSeverity;
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
@Table(name = "DocumentPageQualitySignals", schema = "ethnowear",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_DocumentPageQualitySignals_Assessment_Type_Ordinal",
                columnNames = {"AssessmentId", "SignalType", "SignalOrdinal"}
        )
)
public class DocumentPageQualitySignal extends UpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "AssessmentId", nullable = false)
    private DocumentPageQualityAssessment assessment;

    @Column(name = "SignalType", nullable = false, length = 100)
    private String signalType;

    @Column(name = "SignalOrdinal", nullable = false)
    private Integer signalOrdinal;

    @Column(name = "SignalValueDecimal", precision = 9, scale = 6)
    private BigDecimal signalValueDecimal;

    @Column(name = "SignalValueText", length = 500)
    private String signalValueText;

    @Lob
    @Column(name = "SignalValueJson")
    private String signalValueJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "Severity", nullable = false, length = 50)
    private QualitySignalSeverity severity = QualitySignalSeverity.INFO;

    @Column(name = "Weight", precision = 5, scale = 4)
    private BigDecimal weight;

    @Column(name = "Message", length = 1000)
    private String message;
}
