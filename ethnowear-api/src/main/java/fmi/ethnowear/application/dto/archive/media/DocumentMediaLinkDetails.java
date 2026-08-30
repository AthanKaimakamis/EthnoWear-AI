package fmi.ethnowear.application.dto.archive.media;

public record DocumentMediaLinkDetails(
        Long documentPageMediaId,
        Long mediaAssetId,
        Long documentPageId,
        Long documentId,
        Long sourceReferenceId,
        Long documentSourceId
) {
}
