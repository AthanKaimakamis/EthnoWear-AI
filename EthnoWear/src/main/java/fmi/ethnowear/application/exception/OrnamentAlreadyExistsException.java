package fmi.ethnowear.application.exception;

public class OrnamentAlreadyExistsException extends RuntimeException {

    public OrnamentAlreadyExistsException(String localName) {
        super("Ornament already exists: " + localName);
    }
}
