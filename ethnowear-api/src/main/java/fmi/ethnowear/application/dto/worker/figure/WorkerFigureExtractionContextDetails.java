package fmi.ethnowear.application.dto.worker.figure;

import java.util.List;

public record WorkerFigureExtractionContextDetails(
        long jobId,
        long documentId,
        long documentPageId,
        long documentPageMediaId,
        long inputMediaAssetId,
        long ocrResultId,
        String ocrLayoutJson,
        List<WorkerFigureCandidateDetails> candidates,
        int maximumCaptionCharacters,
        int maximumPrintedNumberCharacters,
        long maximumCropBytes
) {
    public WorkerFigureExtractionContextDetails {
        candidates = List.copyOf(candidates);
    }
}
