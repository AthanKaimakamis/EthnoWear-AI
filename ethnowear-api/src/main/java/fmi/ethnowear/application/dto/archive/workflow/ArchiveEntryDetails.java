package fmi.ethnowear.application.dto.archive.workflow;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemFeatureDetails;
import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaDetails;

import java.util.List;

public record ArchiveEntryDetails(
        ArchiveItemDetails archiveItem,
        List<ArchiveItemFeatureDetails> features,
        List<ArchiveItemMediaDetails> media,
        List<InheritedObservationDetails> inheritedObservations
) {
    public ArchiveEntryDetails {
        features = List.copyOf(features);
        media = List.copyOf(media);
        inheritedObservations = List.copyOf(inheritedObservations);
    }
}
