package fmi.ethnowear.api.dto.archive;

public record SourceReferenceInputDto(
        String chapter,
        Integer pageFrom,
        Integer pageTo,
        String figureNumber,
        String sectionTitle,
        String note
) {
}
