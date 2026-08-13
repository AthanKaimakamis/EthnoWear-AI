package fmi.ethnowear.application.exceptions;

public class OrnamentNotFoundException extends RuntimeException {

    public OrnamentNotFoundException(String localName) {
        super("Ornament not found: " + localName);
    }
}
