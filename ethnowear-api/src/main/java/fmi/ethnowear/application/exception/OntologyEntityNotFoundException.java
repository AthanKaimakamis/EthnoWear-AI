package fmi.ethnowear.application.exception;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import lombok.Getter;

@Getter
public class OntologyEntityNotFoundException extends RuntimeException {

    private final FeatureType entityType;
    private final String localName;

    public OntologyEntityNotFoundException(
            FeatureType entityType,
            String localName
    ) {
        super(entityType + " ontology entity not found: " + localName);
        this.entityType = entityType;
        this.localName = localName;
    }
}
