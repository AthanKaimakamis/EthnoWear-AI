package fmi.ethnowear.application.exception;

public class OrnamentNotFoundException extends RuntimeException {

    public OrnamentNotFoundException(String localName) {
        super("Ornament not found: " + localName);
    }
}
