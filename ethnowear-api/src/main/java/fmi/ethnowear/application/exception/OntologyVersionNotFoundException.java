package fmi.ethnowear.application.exception;

public class OntologyVersionNotFoundException extends RuntimeException {

    public OntologyVersionNotFoundException() {
        super("Active ontology version not found");
    }

    public OntologyVersionNotFoundException(Long versionId) {
        super("Ontology version not found: " + versionId);
    }
}
