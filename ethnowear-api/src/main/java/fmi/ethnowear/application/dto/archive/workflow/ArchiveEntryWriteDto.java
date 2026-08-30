package fmi.ethnowear.application.dto.archive.workflow;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemWriteDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ArchiveEntryWriteDto(
        @NotNull
        @Valid
        ArchiveItemWriteDto archiveItem,

        @Valid
        List<ArchiveEntryFeatureWriteDto> features,

        @Valid
        List<ArchiveEntryMediaWriteDto> media
) {
    public ArchiveEntryWriteDto {
        features = features == null ? List.of() : List.copyOf(features);
        media = media == null ? List.of() : List.copyOf(media);
    }
}
