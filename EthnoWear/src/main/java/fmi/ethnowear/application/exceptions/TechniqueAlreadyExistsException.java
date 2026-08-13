package fmi.ethnowear.application.exceptions;

public class TechniqueAlreadyExistsException extends RuntimeException {

    public TechniqueAlreadyExistsException(String localName) {
        super("Technique already exists: " + localName);
    }
}
