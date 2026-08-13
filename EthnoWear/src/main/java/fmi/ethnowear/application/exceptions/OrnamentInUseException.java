package fmi.ethnowear.application.exceptions;

import fmi.ethnowear.ontology.admin.OntologyReference;

import java.util.List;

public class OrnamentInUseException extends RuntimeException {

    private final List<OntologyReference> references;

    public OrnamentInUseException(String localName, List<OntologyReference> references) {
        super("Ornament is referenced by other ontology resources: " + localName);
        this.references = List.copyOf(references);
    }

    public List<OntologyReference> getReferences() {
        return references;
    }
}
