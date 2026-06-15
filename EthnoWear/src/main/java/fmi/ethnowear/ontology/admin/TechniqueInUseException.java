package fmi.ethnowear.ontology.admin;

import java.util.List;

public class TechniqueInUseException extends RuntimeException {

    private final List<OntologyReference> references;

    public TechniqueInUseException(String localName, List<OntologyReference> references) {
        super("Technique is referenced by other ontology resources: " + localName);
        this.references = List.copyOf(references);
    }

    public List<OntologyReference> getReferences() {
        return references;
    }
}
