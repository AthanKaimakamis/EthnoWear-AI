package fmi.ethnowear.persistence.jpa.entity.document;

import fmi.ethnowear.domain.model.document.figure.FigureExtractionState;
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
@Table(name = "DocumentPageOcrResults", schema = "ethnowear")
public class DocumentPageOcrResult extends UpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "DocumentPageId", nullable = false, updatable = false)
    private DocumentPage documentPage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DocumentPageMediaId", updatable = false)
    private DocumentPageMedia documentPageMedia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ProcessingJobId", updatable = false)
    private DocumentProcessingJob processingJob;

    @Lob
    @Column(name = "RawText", nullable = false, updatable = false)
    private String rawText;

    @Column(name = "OcrEngine", nullable = false, length = 100, updatable = false)
    private String ocrEngine;

    @Column(name = "OcrEngineVersion", length = 100, updatable = false)
    private String ocrEngineVersion;

    @Column(name = "OcrLanguage", length = 20, updatable = false)
    private String ocrLanguage;

    @Column(name = "OcrConfidence", precision = 5, scale = 4, updatable = false)
    private BigDecimal ocrConfidence;

    @Lob
    @Column(name = "ParametersJson", updatable = false)
    private String parametersJson;

    @Lob
    @Column(name = "StructuredOutputJson", updatable = false)
    private String structuredOutputJson;

    @Column(name = "IsCurrent", nullable = false)
    private boolean current;

    @Enumerated(EnumType.STRING)
    @Column(name = "FigureExtractionState", nullable = false, length = 50)
    private FigureExtractionState figureExtractionState =
            FigureExtractionState.NOT_REQUESTED;

    @Column(name = "FigureExtractionMessage", length = 500)
    private String figureExtractionMessage;
}
