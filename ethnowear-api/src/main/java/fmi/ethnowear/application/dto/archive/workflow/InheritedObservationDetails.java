package fmi.ethnowear.application.dto.archive.workflow;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import java.util.List;

public record InheritedObservationDetails(
        FeatureType featureType, String ontologyIri, String ontologyLocalName, List<Origin> origins
) {
    public record Origin(Long mediaAssetId, String fileName, Long sourceReferenceId) {}
    public InheritedObservationDetails { origins = List.copyOf(origins); }
}
