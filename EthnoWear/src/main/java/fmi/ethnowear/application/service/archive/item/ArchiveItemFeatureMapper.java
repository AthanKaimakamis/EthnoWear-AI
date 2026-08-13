package fmi.ethnowear.application.service.archive.item;

import fmi.ethnowear.api.dto.archive.item.ArchiveItemFeatureDetails;
import fmi.ethnowear.api.dto.archive.item.ArchiveItemFeatureWriteDto;
import fmi.ethnowear.dal.entity.ArchiveItem;
import fmi.ethnowear.dal.entity.ArchiveItemFeature;
import fmi.ethnowear.dal.entity.SourceReference;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class ArchiveItemFeatureMapper {

    public void apply(@NonNull ArchiveItemFeature feature, @NonNull ArchiveItemFeatureWriteDto input, ArchiveItem archiveItem, SourceReference sourceReference) {
        feature.setArchiveItem(archiveItem);
        feature.setFeatureType(input.featureType());
        feature.setOntologyIri(input.ontologyIri());
        feature.setOntologyLocalName(input.ontologyLocalName());
        feature.setConfidence(input.confidence());
        feature.setValidated(input.validated());
        feature.setNotes(input.notes());
        feature.setSourceReference(sourceReference);
    }

    public ArchiveItemFeatureDetails toDetails(@NonNull ArchiveItemFeature feature) {
        Long sourceReferenceId = feature.getSourceReference() == null
                ? null
                : feature.getSourceReference().getId();

        return new ArchiveItemFeatureDetails(
                feature.getId(),
                feature.getArchiveItem().getId(),
                feature.getFeatureType(),
                feature.getOntologyIri(),
                feature.getOntologyLocalName(),
                feature.getConfidence(),
                feature.isValidated(),
                feature.getNotes(),
                sourceReferenceId,
                feature.getCreatedAt(),
                feature.getUpdatedAt()
        );
    }
}
