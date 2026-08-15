package fmi.ethnowear.application.exceptions;

import fmi.ethnowear.ontology.admin.model.OntologyReference;

import java.util.List;

public class TechniqueInUseException extends OntologyResourceInUseException {

    public TechniqueInUseException(String localName, List<OntologyReference> references) {
        super("Technique is referenced by other ontology resources: " + localName, references);
    }
}
