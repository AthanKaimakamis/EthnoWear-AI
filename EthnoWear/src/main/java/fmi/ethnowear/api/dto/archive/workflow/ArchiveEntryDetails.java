package fmi.ethnowear.api.dto.archive.workflow;

import fmi.ethnowear.api.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.api.dto.archive.item.ArchiveItemFeatureDetails;
import fmi.ethnowear.api.dto.archive.media.ArchiveItemMediaDetails;

import java.util.List;

public record ArchiveEntryDetails(
        ArchiveItemDetails archiveItem,
        List<ArchiveItemFeatureDetails> features,
        List<ArchiveItemMediaDetails> media
) {
}
