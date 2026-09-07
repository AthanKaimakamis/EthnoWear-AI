package fmi.ethnowear.application.dto.archive.query;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemFeatureDetails;

import java.util.List;

public record ArchiveItemDetailDetails(
        ArchiveItemDetails archiveItem,
        EntitySourceCitationDetails source,
        List<ArchiveItemFeatureDetails> features,
        List<ArchiveItemMediaContentDetails> media,
        List<fmi.ethnowear.application.dto.archive.workflow.InheritedObservationDetails> inheritedObservations,
        List<EntitySourceCitationDetails> imageSources
) {
    public ArchiveItemDetailDetails {
        features = List.copyOf(features);
        media = List.copyOf(media);
        inheritedObservations = List.copyOf(inheritedObservations);
        imageSources = List.copyOf(imageSources);
    }
}
