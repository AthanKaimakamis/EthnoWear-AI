package fmi.ethnowear.application.exceptions;

import fmi.ethnowear.application.enums.FeatureType;
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
