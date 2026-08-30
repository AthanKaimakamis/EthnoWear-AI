package fmi.ethnowear.application.dto.worker.quality;

public record WorkerQualityAssessmentDetails(
        long assessmentId,
        long jobId,
        long documentId,
        long pageId,
        long ocrResultId,
        long inputMediaId,
        boolean existing
) {
}
