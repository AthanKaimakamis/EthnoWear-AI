package fmi.ethnowear.persistence.jpa.projection.document;

import fmi.ethnowear.domain.model.document.quality.QualitySignalSeverity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface DocumentPageQualitySignalProjection {

    Long getId();

    Long getAssessmentId();

    String getSignalType();

    Integer getSignalOrdinal();

    BigDecimal getSignalValueDecimal();

    String getSignalValueText();

    QualitySignalSeverity getSeverity();

    BigDecimal getWeight();

    String getMessage();

    LocalDateTime getCreatedAt();

    LocalDateTime getUpdatedAt();
}
