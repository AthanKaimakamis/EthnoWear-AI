package fmi.ethnowear.application.dto.worker.quality;

import java.math.BigDecimal;

public record WorkerQualityAssessmentContextDetails(
        long jobId,
        long documentId,
        long pageId,
        long ocrResultId,
        long pageMediaId,
        long inputMediaId,
        String rawOcrText,
        BigDecimal ocrConfidence,
        String ocrLanguage,
        String structuredOutputJson,
        String imageMimeType,
        long imageSizeBytes,
        Integer imageWidth,
        Integer imageHeight,
        Integer imageDpi,
        String imageColorMode
) {
}
