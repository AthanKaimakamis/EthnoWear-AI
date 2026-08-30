package fmi.ethnowear.application.service.catalogue.mapper;

import fmi.ethnowear.application.dto.catalogue.EntityCardDetails;
import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.application.dto.catalogue.ConceptEvidenceSummaryDetails;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class EntityCardMapper {

    public EntityCardDetails toDetails(@NonNull EntityOntologyDetails entity) {
        return toDetails(entity, ConceptEvidenceSummaryDetails.empty());
    }

    public EntityCardDetails toDetails(
            @NonNull EntityOntologyDetails entity,
            @NonNull ConceptEvidenceSummaryDetails evidence
    ) {
        return new EntityCardDetails(
                entity.entityType(),
                entity.iri(),
                entity.localName(),
                entity.label(),
                entity.comment(),
                entity.categories(),
                evidence.evidenceCount(),
                evidence.representativeMediaAssetId()
        );
    }
}
