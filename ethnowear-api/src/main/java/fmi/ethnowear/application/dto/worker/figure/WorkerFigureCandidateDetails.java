package fmi.ethnowear.application.dto.worker.figure;

import java.math.BigDecimal;

public record WorkerFigureCandidateDetails(
        long candidateId,
        int candidateOrdinal,
        BigDecimal normalizedX,
        BigDecimal normalizedY,
        BigDecimal normalizedWidth,
        BigDecimal normalizedHeight,
        String rawCaptionText,
        BigDecimal detectionConfidence
) {
}
