package fmi.ethnowear.application.dto.worker.ocr;

public record WorkerOcrResultDetails(
        long ocrResultId,
        long jobId,
        long documentId,
        long pageId,
        long inputMediaId,
        boolean existing
) {
}
