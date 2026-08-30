package fmi.ethnowear.application.exception;

public class TechniqueAlreadyExistsException extends RuntimeException {

    public TechniqueAlreadyExistsException(String localName) {
        super("Technique already exists: " + localName);
    }
}
