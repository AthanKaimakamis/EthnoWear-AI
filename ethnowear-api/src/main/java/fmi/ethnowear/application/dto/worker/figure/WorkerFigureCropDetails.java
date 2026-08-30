package fmi.ethnowear.application.dto.worker.figure;

public record WorkerFigureCropDetails(
        long figureId,
        long documentPageId,
        long mediaAssetId,
        int figureOrdinal,
        boolean existing
) {
}
