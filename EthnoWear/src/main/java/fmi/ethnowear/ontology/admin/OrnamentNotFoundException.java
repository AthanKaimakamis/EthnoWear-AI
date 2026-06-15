package fmi.ethnowear.ontology.admin;

public class OrnamentNotFoundException extends RuntimeException {

    public OrnamentNotFoundException(String localName) {
        super("Ornament not found: " + localName);
    }
}
