package fmi.ethnowear.application.dto.document.query.processing;

public record ProcessingJobTargetDetails(
        String type,
        Long documentId,
        Long documentPageId,
        Long inputMediaAssetId,
        Long knowledgeChunkId
) {
}
