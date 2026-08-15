package fmi.ethnowear.application.exceptions;

import fmi.ethnowear.ontology.admin.model.OntologyReference;

import java.util.List;

public class OrnamentInUseException extends OntologyResourceInUseException {

    public OrnamentInUseException(String localName, List<OntologyReference> references) {
        super("Ornament is referenced by other ontology resources: " + localName, references);
    }
}
