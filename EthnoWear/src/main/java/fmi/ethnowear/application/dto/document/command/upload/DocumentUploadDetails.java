package fmi.ethnowear.application.dto.document.command.upload;

public record DocumentUploadDetails(
        Long documentId,
        Long mediaAssetId,
        Long documentPageId,
        Long documentPageMediaId,
        Long processingJobId
) {
}
