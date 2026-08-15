package fmi.ethnowear.api.dto.archive.query;

import fmi.ethnowear.api.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.api.dto.archive.item.ArchiveItemFeatureDetails;

import java.util.List;

public record ArchiveItemDetailDetails(
        ArchiveItemDetails archiveItem,
        EntitySourceCitationDetails source,
        List<ArchiveItemFeatureDetails> features,
        List<ArchiveItemMediaContentDetails> media
) {
    public ArchiveItemDetailDetails {
        features = List.copyOf(features);
        media = List.copyOf(media);
    }
}
