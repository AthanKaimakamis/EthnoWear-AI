package fmi.ethnowear.ontology.admin;

public class OrnamentAlreadyExistsException extends RuntimeException {

    public OrnamentAlreadyExistsException(String localName) {
        super("Ornament already exists: " + localName);
    }
}
