package fmi.ethnowear.application.dto.worker.vision;

public record WorkerVisionAssessmentDetails(
        long assessmentId,
        long suggestionId,
        long jobId,
        long documentId,
        long pageId,
        long ocrResultId,
        long inputMediaId,
        boolean existing
) {
}
