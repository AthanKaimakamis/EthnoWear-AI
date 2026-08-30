package fmi.ethnowear.application.dto.document.query;

import fmi.ethnowear.domain.model.document.quality.QualityStatus;

import java.math.BigDecimal;

public record DocumentPageQueryDto(
        QualityStatus qualityLevel,
        BigDecimal minimumQualityScore,
        BigDecimal maximumQualityScore
) {
}
