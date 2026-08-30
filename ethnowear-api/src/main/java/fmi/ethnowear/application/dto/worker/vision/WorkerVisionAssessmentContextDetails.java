package fmi.ethnowear.application.dto.worker.vision;

import fmi.ethnowear.domain.model.document.quality.QualityStatus;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;

import java.math.BigDecimal;
import java.util.List;

public record WorkerVisionAssessmentContextDetails(
        long jobId,
        long documentId,
        long documentPageId,
        long ocrResultId,
        long documentPageMediaId,
        long inputMediaId,
        String rawOcrText,
        BigDecimal ocrConfidence,
        String ocrLanguage,
        String structuredOutputJson,
        long deterministicAssessmentId,
        TranscriptionApprovalState transcriptionApprovalState,
        ReviewState reviewState,
        IndexingState indexingState,
        QualityStatus deterministicQualityStatus,
        BigDecimal deterministicOverallScore,
        String deterministicSummary,
        String deterministicLimitations,
        List<WorkerVisionQualitySignalDetails> deterministicSignals,
        String imageMimeType,
        long imageSizeBytes,
        Integer imageWidth,
        Integer imageHeight,
        Integer imageDpi,
        String imageColorMode
) {

    public WorkerVisionAssessmentContextDetails {
        deterministicSignals = List.copyOf(deterministicSignals);
    }
}
