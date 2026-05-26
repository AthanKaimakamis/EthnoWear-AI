package fmi.ethnowear.api.dto.archive;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateArchiveItemFullRequest(
        @Valid
        @NotNull
        SourceInputDto source,

        @Valid
        @NotNull
        SourceReferenceInputDto sourceRefence,

        @Valid
        @NotNull
        ArchiveItemInputDto archiveItem,

        @Valid
        List<ArchiveFeatureInputDto> features,

        @Valid
        List<MediaAssetInputDto> media,

        @Valid
        List<KnowledgeChunkInputDto> knowledgeChunks
) {
}
