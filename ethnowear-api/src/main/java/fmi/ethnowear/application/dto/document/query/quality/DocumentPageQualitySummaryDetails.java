package fmi.ethnowear.application.dto.document.query.quality;

import fmi.ethnowear.domain.model.document.quality.QualityStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DocumentPageQualitySummaryDetails(
        Long assessmentId,
        BigDecimal score,
        BigDecimal percentage,
        QualityStatus qualityLevel,
        int passedChecks,
        int failedChecks,
        LocalDateTime assessedAt
) {
}
