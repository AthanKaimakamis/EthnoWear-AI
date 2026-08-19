package fmi.ethnowear.application.service.catalogue.mapper;

import fmi.ethnowear.application.dto.catalogue.EntityCardDetails;
import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
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
