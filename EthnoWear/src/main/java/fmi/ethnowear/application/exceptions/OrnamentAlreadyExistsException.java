package fmi.ethnowear.application.exceptions;

public class OrnamentAlreadyExistsException extends RuntimeException {

    public OrnamentAlreadyExistsException(String localName) {
        super("Ornament already exists: " + localName);
    }
}
