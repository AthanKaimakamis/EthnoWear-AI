package fmi.ethnowear.application.dto.worker.vision;

import fmi.ethnowear.domain.model.document.quality.QualitySignalSeverity;

import java.math.BigDecimal;

public record WorkerVisionQualitySignalDetails(
        String type,
        BigDecimal decimalValue,
        String textValue,
        QualitySignalSeverity severity,
        BigDecimal weight,
        String safeMessage
) {
}
