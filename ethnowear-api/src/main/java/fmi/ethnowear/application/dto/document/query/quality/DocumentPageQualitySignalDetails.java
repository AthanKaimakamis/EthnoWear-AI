package fmi.ethnowear.application.dto.document.query.quality;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.document.quality.QualitySignalSeverity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DocumentPageQualitySignalDetails(
        Long id,
        Long assessmentId,
        String signalType,
        Integer signalOrdinal,
        BigDecimal signalValueDecimal,
        String signalValueText,
        QualitySignalSeverity severity,
        BigDecimal weight,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {
}
