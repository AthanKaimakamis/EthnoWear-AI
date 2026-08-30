package fmi.ethnowear.application.exception;

import fmi.ethnowear.domain.model.ontology.OntologyReference;

import java.util.List;

public class OrnamentInUseException extends OntologyResourceInUseException {

    public OrnamentInUseException(String localName, List<OntologyReference> references) {
        super("Ornament is referenced by other ontology resources: " + localName, references);
    }
}
