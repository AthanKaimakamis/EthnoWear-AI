package fmi.ethnowear.api.dto.archive;

public record CreateArchiveItemResponse(
        Long archiveItemId,
        Long sourceId,
        Long sourceReferenceId,
        int featureCount,
        int mediaCount,
        int knowledgeChunkCount
) {
}
