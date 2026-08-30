package fmi.ethnowear.application.dto.document.query.chunk;

public record ChunkGenerationBlockerDetails(
        Long documentPageId,
        String code,
        String message
) {
}
