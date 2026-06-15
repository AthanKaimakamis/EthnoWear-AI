package fmi.ethnowear.ontology.admin;

public class TechniqueNotFoundException extends RuntimeException {

    public TechniqueNotFoundException(String localName) {
        super("Technique not found: " + localName);
    }
}
