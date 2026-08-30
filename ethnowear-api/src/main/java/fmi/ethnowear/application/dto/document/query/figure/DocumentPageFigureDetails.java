package fmi.ethnowear.application.dto.document.query.figure;

import fmi.ethnowear.domain.model.document.figure.FigureReviewState;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DocumentPageFigureDetails(
        long id,
        long documentPageId,
        long documentPageMediaId,
        long mediaAssetId,
        Long sourceReferenceId,
        Long figureCandidateId,
        int figureOrdinal,
        String printedFigureNumber,
        BigDecimal normalizedX,
        BigDecimal normalizedY,
        BigDecimal normalizedWidth,
        BigDecimal normalizedHeight,
        String rawCaptionText,
        String correctedCaptionText,
        FigureReviewState reviewState,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String reviewReason,
        BigDecimal detectionConfidence,
        long processingJobId,
        int producingAttempt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String version
) {
}
