package fmi.ethnowear.application.exception;

import fmi.ethnowear.domain.model.ontology.OntologyReference;

import java.util.List;

public class TechniqueInUseException extends OntologyResourceInUseException {

    public TechniqueInUseException(String localName, List<OntologyReference> references) {
        super("Technique is referenced by other ontology resources: " + localName, references);
    }
}
