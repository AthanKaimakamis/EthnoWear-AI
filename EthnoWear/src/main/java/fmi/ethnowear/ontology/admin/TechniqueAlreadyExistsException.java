package fmi.ethnowear.ontology.admin;

public class TechniqueAlreadyExistsException extends RuntimeException {

    public TechniqueAlreadyExistsException(String localName) {
        super("Technique already exists: " + localName);
    }
}
