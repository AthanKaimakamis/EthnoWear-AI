package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.api.dto.catalogue.EntityCardDetails;
import fmi.ethnowear.api.dto.catalogue.EntityOntologyDetails;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class EntityCardMapper {

    public EntityCardDetails toDetails(@NonNull EntityOntologyDetails entity) {
        return new EntityCardDetails(
                entity.entityType(),
                entity.iri(),
                entity.localName(),
                entity.label(),
                entity.comment(),
                entity.categories()
        );
    }
}
